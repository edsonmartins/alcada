package app.alcada.notificacao;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import app.alcada.notificacao.internal.PortalTokens;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** Cenários do 007 — portal externo sem login. */
@QuarkusTest
class PortalExternoTest {

    @Inject PortalTokens tokens;
    @Inject EntityManager em;

    @Test
    void link_assinado_mostra_estado_publico_com_no_index() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        String token = emitir(org, pend, "documento fiscal assinado", futuro());

        String corpo = given().when().get("/p/" + token).then()
                .statusCode(200)
                .header("X-Robots-Tag", containsString("noindex"))
                .body("estado", is("em_andamento"))
                .body("oQueFalta", is("documento fiscal assinado"))
                .extract().asString();

        // fronteira ADR-0013: nenhum campo interno
        for (String interno : new String[]{"titulo", "quemEspera", "classe", "dono", "status", "delega"}) {
            assertFalse(corpo.toLowerCase().contains(interno.toLowerCase()), "vazou campo interno: " + interno);
        }
    }

    @Test
    void token_expirado_responde_404() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        String token = emitir(org, pend, "x", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        given().when().get("/p/" + token).then().statusCode(404);
    }

    @Test
    void token_revogado_responde_404() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        var t = emitirRaw(org, pend, "x", futuro());
        QuarkusTransaction.requiringNew().run(() -> tokens.revogar(org, t.tokenId()));
        given().when().get("/p/" + t.token()).then().statusCode(404);
    }

    @Test
    void token_invalido_responde_404() {
        given().when().get("/p/" + "nao-existe-token").then().statusCode(404)
                .header("X-Robots-Tag", containsString("noindex"));
    }

    @Test
    void fechada_aparece_como_concluido_sem_revelar_como() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "FECHADA");
        // fechada agora → dentro da folga
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "UPDATE pendencia SET fechada_em = now() WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, pend).executeUpdate());
        String token = emitir(org, pend, "nada", futuro());

        String corpo = given().when().get("/p/" + token).then().statusCode(200)
                .body("estado", is("concluido")).extract().asString();
        assertFalse(corpo.toLowerCase().contains("ausencia") || corpo.toLowerCase().contains("delega"),
                "não revela como foi fechada");
    }

    @Test
    void token_expira_junto_com_o_fechamento_mais_folga() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "FECHADA");
        String token = emitir(org, pend, "x", futuro()); // expira_em bem no futuro

        // fechada há mais de 7 dias (folga default) → efetivamente expirado
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "UPDATE pendencia SET fechada_em = now() - interval '8 days' WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, pend).executeUpdate());
        given().when().get("/p/" + token).then().statusCode(404);
    }

    @Test
    void o_que_falta_e_o_texto_curado() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        String token = emitir(org, pend, "documento fiscal assinado", futuro());
        given().when().get("/p/" + token).then().statusCode(200)
                .body("oQueFalta", is("documento fiscal assinado"));
    }

    @Test
    void o_banco_guarda_hash_nao_o_token_cru() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        var t = emitirRaw(org, pend, "x", futuro());

        String hashArmazenado = (String) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT token_hash FROM token_portal WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, t.tokenId()).getSingleResult());
        assertNotEquals(t.token(), hashArmazenado, "não guarda o token cru");
        assertTrue(hashArmazenado.matches("[0-9a-f]{64}"), "guarda o SHA-256 hex");
    }

    @Test
    void token_escopado_nao_revela_outra_pendencia() {
        OrgId org = novaOrg();
        UUID pendA = pendencia(org, "ENTRADA");
        pendencia(org, "ENTRADA"); // pendência B, não escopada
        String tokenA = emitir(org, pendA, "falta-de-A", futuro());
        given().when().get("/p/" + tokenA).then().statusCode(200).body("oQueFalta", is("falta-de-A"));
    }

    // ---- helpers -----------------------------------------------------------

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    private UUID pendencia(OrgId org, String status) {
        UUID pend = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status)
                VALUES (?, ?, 'segredo interno', 'DECISAO', 'SEMANA', ?)
                """).setParameter(1, pend).setParameter(2, org.valor()).setParameter(3, status).executeUpdate());
        return pend;
    }

    private PortalTokens.TokenEmitido emitirRaw(OrgId org, UUID pend, String oQueFalta, OffsetDateTime expira) {
        return QuarkusTransaction.requiringNew().call(() -> tokens.emitir(org, pend, oQueFalta, expira));
    }

    private String emitir(OrgId org, UUID pend, String oQueFalta, OffsetDateTime expira) {
        return emitirRaw(org, pend, oQueFalta, expira).token();
    }

    private static OffsetDateTime futuro() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(30);
    }
}
