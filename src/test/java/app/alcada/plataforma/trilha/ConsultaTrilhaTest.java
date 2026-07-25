package app.alcada.plataforma.trilha;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Consulta da trilha por pendência: isolada por organização (INV-15) e exposta
 * em {@code GET /v1/pendencias/{id}/trilha}.
 */
@QuarkusTest
class ConsultaTrilhaTest {

    @Inject Trilha trilha;
    @Inject ConsultaTrilha consulta;

    @Test
    void consulta_isolada_por_organizacao() {
        OrgId a = new OrgId(UUID.randomUUID());
        OrgId b = new OrgId(UUID.randomUUID());
        UUID pend = UUID.randomUUID(); // mesmo id em dois tenants: caso extremo

        QuarkusTransaction.requiringNew().run(() -> {
            trilha.registrar(new EventoTrilha(a, pend, TipoEvento.CAPTADA,
                    Ator.sistemaMotor("captura"), null, "ENTRADA", null, null));
            trilha.registrar(new EventoTrilha(b, pend, TipoEvento.CAPTADA,
                    Ator.sistemaMotor("captura"), null, "ENTRADA", null, null));
        });

        assertEquals(1, QuarkusTransaction.requiringNew().call(() -> consulta.daPendencia(a, pend)).size());
        assertEquals(1, QuarkusTransaction.requiringNew().call(() -> consulta.daPendencia(b, pend)).size());
    }

    @Test
    void endpoint_retorna_trilha_da_pendencia() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID pend = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> trilha.registrar(new EventoTrilha(
                org, pend, TipoEvento.CAPTADA, Ator.sistemaMotor("captura"),
                null, "ENTRADA", null, null)));

        given()
                .header("X-Org-Id", org.valor().toString())
        .when()
                .get("/v1/pendencias/" + pend + "/trilha")
        .then()
                .statusCode(200)
                .body("size()", is(1));
    }

    @Test
    void endpoint_sem_org_id_responde_400_problem_json() {
        given()
        .when()
                .get("/v1/pendencias/" + UUID.randomUUID() + "/trilha")
        .then()
                .statusCode(400)
                .contentType("application/problem+json");
    }
}
