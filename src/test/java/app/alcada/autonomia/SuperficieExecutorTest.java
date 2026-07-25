package app.alcada.autonomia;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import app.alcada.autonomia.internal.MotorAutonomia;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoRegistrado;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** Cenários da superfície do executor (005). */
@QuarkusTest
class SuperficieExecutorTest {

    @Inject MotorAutonomia motor;
    @Inject ConsultaTrilha trilha;
    @Inject EntityManager em;

    @Test
    void executor_ve_apenas_as_delegacoes_dele() {
        OrgId org = novaOrg();
        UUID gestor = UUID.randomUUID();
        UUID execA = UUID.randomUUID();
        UUID execB = UUID.randomUUID();
        delegar(org, gestor, execA);
        delegar(org, gestor, execB);

        given().header("X-Org-Id", org.valor().toString()).header("X-Pessoa-Id", execA.toString())
        .when().get("/v1/delegacoes")
        .then().statusCode(200).body("size()", is(1)).body("[0].donoId", is(execA.toString()));
    }

    @Test
    void executor_conclui_a_delegacao() {
        OrgId org = novaOrg();
        UUID exec = UUID.randomUUID();
        UUID pend = pendencia(org);
        UUID deleg = motor.delegar(org, pend, exec, "N2", agora(), UUID.randomUUID());

        motor.concluir(org, deleg, "aplicado o reajuste", exec);

        assertEquals("EXECUTADA", statusDelegacao(org, deleg));
        assertEquals("FECHADA", statusPendencia(org, pend));
        assertTrue(tipos(org, pend).contains("EXECUTADA"));
        assertEquals(1L, countOutbox(org, "delegacao.executada"));
        assertEquals(1L, countOutbox(org, "item.fechado"), "aviso ao solicitante enfileirado (entrega no 006)");
    }

    @Test
    void executor_devolve_ao_gestor() {
        OrgId org = novaOrg();
        UUID exec = UUID.randomUUID();
        UUID pend = pendencia(org);
        UUID deleg = motor.delegar(org, pend, exec, "N2", agora(), UUID.randomUUID());

        motor.devolver(org, deleg, "não é comigo", exec);

        assertEquals("DEVOLVIDA", statusDelegacao(org, deleg));
        assertEquals("ENTRADA", statusPendencia(org, pend));
        assertTrue(tipos(org, pend).contains("DEVOLVIDA_PELO_EXECUTOR"), "evento próprio, não ESCALADA");
        assertTrue(!tipos(org, pend).contains("ESCALADA"));
        assertEquals(1L, countOutbox(org, "delegacao.devolvida"));
        assertEquals(0L, countOutbox(org, "delegacao.executada_por_ausencia"), "nenhuma execução");
    }

    @Test
    void executor_nao_age_em_delegacao_de_outro() {
        OrgId org = novaOrg();
        UUID dono = UUID.randomUUID();
        UUID intruso = UUID.randomUUID();
        UUID pend = pendencia(org);
        UUID deleg = motor.delegar(org, pend, dono, "N2", agora(), UUID.randomUUID());

        given().header("X-Org-Id", org.valor().toString()).header("X-Pessoa-Id", intruso.toString())
                .contentType("application/json").body("{\"resultado\":\"x\"}")
        .when().post("/v1/delegacoes/" + deleg + "/concluir")
        .then().statusCode(403).body("type", containsString("delegacao.nao_e_sua"));

        assertEquals("ABERTA", statusDelegacao(org, deleg), "estado não muda");
    }

    @Test
    void acao_em_estado_invalido_e_recusada() {
        OrgId org = novaOrg();
        UUID exec = UUID.randomUUID();
        UUID pend = pendencia(org);
        UUID deleg = motor.delegar(org, pend, exec, "N2", agora(), UUID.randomUUID());
        motor.concluir(org, deleg, "ok", exec);

        given().header("X-Org-Id", org.valor().toString()).header("X-Pessoa-Id", exec.toString())
                .contentType("application/json").body("{\"resultado\":\"de novo\"}")
        .when().post("/v1/delegacoes/" + deleg + "/concluir")
        .then().statusCode(409).body("type", containsString("estado_invalido"));
    }

    @Test
    void get_escopado_isola_por_organizacao() {
        OrgId orgA = novaOrg();
        OrgId orgB = novaOrg();
        UUID exec = UUID.randomUUID();
        delegar(orgB, UUID.randomUUID(), exec); // delegação do exec, mas na org B

        // mesmo executor, consultando na org A: não vê a da org B
        given().header("X-Org-Id", orgA.valor().toString()).header("X-Pessoa-Id", exec.toString())
        .when().get("/v1/delegacoes")
        .then().statusCode(200).body("size()", is(0));
    }

    // ---- helpers -----------------------------------------------------------

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome, sku) VALUES (?, 'Org', 'CLOUD')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    private UUID pendencia(OrgId org) {
        UUID pend = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status)
                VALUES (?, ?, 'Item', 'DECISAO', 'SEMANA', 'ENTRADA')
                """).setParameter(1, pend).setParameter(2, org.valor()).executeUpdate());
        return pend;
    }

    private UUID delegar(OrgId org, UUID gestor, UUID dono) {
        return motor.delegar(org, pendencia(org), dono, "N2", agora(), gestor);
    }

    private static OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String statusDelegacao(OrgId org, UUID id) {
        return (String) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT status FROM delegacao WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, id).getSingleResult());
    }

    private String statusPendencia(OrgId org, UUID pend) {
        return (String) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT status FROM pendencia WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, pend).getSingleResult());
    }

    private long countOutbox(OrgId org, String tipo) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT count(*) FROM outbox WHERE org_id = ? AND tipo = ?")
                .setParameter(1, org.valor()).setParameter(2, tipo).getSingleResult())).longValue();
    }

    private List<String> tipos(OrgId org, UUID pend) {
        return QuarkusTransaction.requiringNew().call(() ->
                trilha.daPendencia(org, pend).stream().map(EventoRegistrado::tipo).toList());
    }
}
