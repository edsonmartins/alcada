package app.alcada.captura;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoRegistrado;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** Escape manual (ADR-0005) — plano B da demo: inserir pendência sem Linktor. */
@QuarkusTest
class EscapeManualTest {

    @Inject EntityManager em;
    @Inject ConsultaTrilha trilha;

    @Test
    void escape_cria_pendencia_em_entrada_com_trilha() {
        OrgId org = novaOrg();
        UUID gestor = UUID.randomUUID();

        String id = given().header("X-Org-Id", org.valor().toString()).header("X-Pessoa-Id", gestor.toString())
                .contentType("application/json")
                .body("{\"titulo\":\"Aprovar reembolso do Rafael\",\"quemEspera\":\"Rafael\"}")
        .when().post("/v1/pendencias")
        .then().statusCode(201).extract().path("id");

        // aparece na fila em ENTRADA (delegável para N2)
        given().header("X-Org-Id", org.valor().toString())
        .when().get("/v1/pendencias?status=ENTRADA")
        .then().statusCode(200).body("size()", is(1));

        List<String> tipos = QuarkusTransaction.requiringNew().call(() ->
                trilha.daPendencia(org, UUID.fromString(id)).stream().map(EventoRegistrado::tipo).toList());
        assertTrue(tipos.contains("CAPTADA"), "trilha registra a captura por escape");
        assertEquals("ENTRADA", QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT status FROM pendencia WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, UUID.fromString(id)).getSingleResult()));
    }

    @Test
    void escape_sem_titulo_e_400() {
        OrgId org = novaOrg();
        given().header("X-Org-Id", org.valor().toString()).header("X-Pessoa-Id", UUID.randomUUID().toString())
                .contentType("application/json").body("{\"quemEspera\":\"x\"}")
        .when().post("/v1/pendencias").then().statusCode(400);
    }

    @Test
    void escape_classe_invalida_e_422() {
        OrgId org = novaOrg();
        given().header("X-Org-Id", org.valor().toString()).header("X-Pessoa-Id", UUID.randomUUID().toString())
                .contentType("application/json").body("{\"titulo\":\"x\",\"classe\":\"QUALQUER\"}")
        .when().post("/v1/pendencias").then().statusCode(422);
    }

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }
}
