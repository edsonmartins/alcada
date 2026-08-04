package app.alcada.notificacao;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import app.alcada.notificacao.port.ContasCalendario;
import app.alcada.plataforma.cripto.port.Cofre;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Conta de calendário do gestor (RFC-0009 F2.3b): conectar por OAuth, ver o
 * estado e revogar. O token é do gestor, não do tenant, e nunca sai do servidor
 * em claro — no banco ele está cifrado.
 */
@QuarkusTest
class ContaCalendarioTest {

    @Inject ContasCalendario contas;
    @Inject Cofre cofre;
    @Inject EntityManager em;

    // C15b — conectar guarda a conta e o estado passa a dizer que há calendário
    @Test
    void conecta_le_o_estado_e_revoga() {
        String org = UUID.randomUUID().toString();
        String gestor = UUID.randomUUID().toString();

        given().header("X-Org-Id", org).header("X-Pessoa-Id", gestor)
        .when().get("/v1/calendario")
        .then().statusCode(200).body("conectado", Matchers.is(false));

        given().header("X-Org-Id", org).header("X-Pessoa-Id", gestor)
                .contentType("application/json")
                .body("{\"codigo\":\"cod-123\",\"redirectUri\":\"https://alcada.vendax.ai/callback\"}")
        .when().post("/v1/calendario")
        .then().statusCode(200)
                .body("conectado", Matchers.is(true))
                .body("provedor", Matchers.equalTo("GOOGLE"))
                .body("$", Matchers.not(Matchers.hasKey("accessToken")));

        given().header("X-Org-Id", org).header("X-Pessoa-Id", gestor)
        .when().get("/v1/calendario")
        .then().statusCode(200).body("conectado", Matchers.is(true));

        given().header("X-Org-Id", org).header("X-Pessoa-Id", gestor)
        .when().delete("/v1/calendario")
        .then().statusCode(204);

        given().header("X-Org-Id", org).header("X-Pessoa-Id", gestor)
        .when().get("/v1/calendario")
        .then().statusCode(200).body("conectado", Matchers.is(false));
    }

    // Consentimento recusado não conecta ninguém
    @Test
    void consentimento_invalido_recusado() {
        given().header("X-Org-Id", UUID.randomUUID().toString())
                .header("X-Pessoa-Id", UUID.randomUUID().toString())
                .contentType("application/json")
                .body("{\"codigo\":\"recusar\"}")
        .when().post("/v1/calendario")
        .then().statusCode(422).contentType("application/problem+json");
    }

    // O banco não guarda o token em claro (ADR-0011: segredo é segredo)
    @Test
    void token_fica_cifrado_no_banco() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID gestor = UUID.randomUUID();
        contas.salvar(org, gestor, new ContasCalendario.Conta(
                "GOOGLE", "token-secreto", "refresh-secreto", null, "events"));

        String guardado = (String) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT access_token FROM conta_calendario WHERE org_id = ? AND pessoa_id = ?")
                .setParameter(1, org.valor()).setParameter(2, gestor).getSingleResult());

        assertNotEquals("token-secreto", guardado, "não guarda em claro");
        assertFalse(guardado.contains("token-secreto"));
        assertEquals("token-secreto", cofre.decifrar(guardado), "mas volta com a chave");
        assertEquals("token-secreto", contas.doGestor(org, gestor).orElseThrow().accessToken());
    }

    // INV-15 — a conta é do gestor naquela organização
    @Test
    void conta_nao_atravessa_organizacoes() {
        OrgId a = new OrgId(UUID.randomUUID());
        OrgId b = new OrgId(UUID.randomUUID());
        UUID gestor = UUID.randomUUID();
        contas.salvar(a, gestor, new ContasCalendario.Conta("GOOGLE", "t", null, null, null));

        assertTrue(contas.doGestor(a, gestor).isPresent());
        assertTrue(contas.doGestor(b, gestor).isEmpty(), "mesma pessoa, outro tenant: sem conta");
    }

    @Test
    void cofre_ida_e_volta_com_nonce_diferente_a_cada_vez() {
        String claro = "refresh-token-do-gestor";
        String c1 = cofre.cifrar(claro);
        String c2 = cofre.cifrar(claro);

        assertNotEquals(c1, c2, "nonce por operação: o mesmo texto não gera o mesmo cifrado");
        assertEquals(claro, cofre.decifrar(c1));
        assertEquals(claro, cofre.decifrar(c2));
    }
}
