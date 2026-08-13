package app.alcada.autonomia;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import app.alcada.autonomia.internal.MotorAutonomia;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.scheduler.internal.WorkerJobs;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoRegistrado;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** Os 8 cenários do spec.md do motor de autonomia N2. */
@QuarkusTest
class MotorAutonomiaTest {

    @Inject MotorAutonomia motor;
    @Inject WorkerJobs worker;
    @Inject ConsultaTrilha trilha;
    @Inject EntityManager em;

    // ---- Cenário: execução por ausência -----------------------------------
    @Test
    void execucao_por_ausencia() {
        Ctx c = novo("DECISAO");
        UUID deleg = motor.delegar(c.org, c.pend, c.dono, "N2", agora(), c.gestor);
        motor.propor(c.org, deleg, "reajuste conforme índice", c.dono);

        worker.processarDevidos(); // vencimento: PROPOSTA → AGUARDANDO_JANELA
        assertEquals("AGUARDANDO_JANELA", statusDelegacao(c.org, deleg));
        assertEquals(0L, countOutbox(c.org, "delegacao.executada_por_ausencia"), "nenhum efeito antes da janela");

        adiantar(c.org, "AUT_VIRADA");
        worker.processarDevidos(); // virada: executa por ausência
        assertEquals("EXECUTADA", statusDelegacao(c.org, deleg));
        assertEquals("FECHADA", statusPendencia(c.org, c.pend));
        assertEquals(1L, countOutbox(c.org, "delegacao.executada_por_ausencia"));
        assertTrue(tipos(c.org, c.pend).contains("EXECUTADA_POR_AUSENCIA"));
    }

    // ---- Cenário: lembretes a 50% e 90% do prazo (002) --------------------
    @Test
    void lembretes_50_e_90_notificam_executor_e_gestor() {
        Ctx c = novo("DECISAO");
        UUID deleg = motor.delegar(c.org, c.pend, c.dono, "N2", futuro(), c.gestor);

        // O roteamento persistente é coberto por SchedulerReinicioTest; aqui o
        // executor é chamado diretamente para não depender de jobs de outras classes.
        motor.aoLembrete(c.org, deleg, false);
        assertEquals(1L, countOutbox(c.org, "delegacao.lembrete_executor"), "50%: cutuca o executor");
        assertEquals(0L, countOutbox(c.org, "delegacao.lembrete_gestor"));

        motor.aoLembrete(c.org, deleg, true);
        assertEquals(1L, countOutbox(c.org, "delegacao.lembrete_gestor"), "90%: avisa o gestor");
    }

    @Test
    void lembrete_nao_notifica_se_delegacao_ja_saiu_da_janela() {
        Ctx c = novo("DECISAO");
        UUID deleg = motor.delegar(c.org, c.pend, c.dono, "N2", futuro(), c.gestor);
        motor.intervir(c.org, c.pend, c.gestor); // gestor devolve → DEVOLVIDA

        adiantar(c.org, "AUT_LEMBRETE_50");
        worker.processarDevidos();
        assertEquals(0L, countOutbox(c.org, "delegacao.lembrete_executor"),
                "sem lembrete depois que a delegação já saiu da janela");
    }

    @Test
    void proposta_suprime_lembretes_50_e_90() {
        Ctx c = novo("DECISAO");
        UUID deleg = motor.delegar(c.org, c.pend, c.dono, "N2", futuro(), c.gestor);
        motor.propor(c.org, deleg, "proposta pronta", c.dono);
        motor.aoLembrete(c.org, deleg, false);
        motor.aoLembrete(c.org, deleg, true);
        assertEquals(0L, countOutbox(c.org, "delegacao.lembrete_executor"));
        assertEquals(0L, countOutbox(c.org, "delegacao.lembrete_gestor"));
    }

    @Test
    void reprocesso_do_mesmo_lembrete_nao_duplica() {
        Ctx c = novo("DECISAO");
        UUID deleg = motor.delegar(c.org, c.pend, c.dono, "N2", futuro(), c.gestor);
        motor.aoLembrete(c.org, deleg, false);
        motor.aoLembrete(c.org, deleg, false);
        assertEquals(1L, countOutbox(c.org, "delegacao.lembrete_executor"));
    }

    // ---- Cenário: gestor interrompe antes do prazo ------------------------
    @Test
    void gestor_interrompe_antes_do_prazo() {
        Ctx c = novo("DECISAO");
        UUID deleg = motor.delegar(c.org, c.pend, c.dono, "N2", futuro(), c.gestor);
        motor.intervir(c.org, c.pend, c.gestor);

        assertEquals("DEVOLVIDA", statusDelegacao(c.org, deleg));
        assertEquals("ENTRADA", statusPendencia(c.org, c.pend));
        assertEquals(0L, countOutbox(c.org, "delegacao.executada_por_ausencia"));
        assertTrue(tipos(c.org, c.pend).contains("INTERROMPIDA"));
    }

    // ---- Cenário: gestor desfaz dentro da janela --------------------------
    @Test
    void gestor_desfaz_dentro_da_janela() {
        Ctx c = novo("DECISAO");
        UUID deleg = motor.delegar(c.org, c.pend, c.dono, "N2", agora(), c.gestor);
        motor.propor(c.org, deleg, "proposta", c.dono);
        worker.processarDevidos(); // → AGUARDANDO_JANELA

        motor.desfazer(c.org, c.pend, c.gestor);
        assertEquals("DEVOLVIDA", statusDelegacao(c.org, deleg));
        assertEquals("ENTRADA", statusPendencia(c.org, c.pend));
        assertTrue(tipos(c.org, c.pend).contains("DESFEITA_NA_JANELA"));

        adiantar(c.org, "AUT_VIRADA");
        worker.processarDevidos(); // virada: no-op (desfeita)
        assertEquals(0L, countOutbox(c.org, "delegacao.executada_por_ausencia"), "não executa após desfazer");
    }

    // ---- Cenário: desfazer fora da janela é recusado ----------------------
    @Test
    void desfazer_fora_da_janela_e_recusado() {
        Ctx c = novo("DECISAO");
        UUID deleg = motor.delegar(c.org, c.pend, c.dono, "N2", agora(), c.gestor);
        motor.propor(c.org, deleg, "proposta", c.dono);
        worker.processarDevidos();
        adiantar(c.org, "AUT_VIRADA");
        worker.processarDevidos(); // executada

        int trilhaAntes = tipos(c.org, c.pend).size();
        given()
                .header("X-Org-Id", c.org.valor().toString())
                .header("X-Pessoa-Id", c.gestor.toString())
        .when()
                .post("/v1/pendencias/" + c.pend + "/desfazer")
        .then()
                .statusCode(409)
                .contentType("application/problem+json")
                .body("type", org.hamcrest.Matchers.containsString("janela.expirada"));
        assertEquals(trilhaAntes, tipos(c.org, c.pend).size(), "409 não altera a trilha");
    }

    // ---- Cenário: silêncio de ambos não executa em branco -----------------
    @Test
    void silencio_de_ambos_nao_executa_em_branco() {
        Ctx c = novo("DECISAO");
        UUID deleg = motor.delegar(c.org, c.pend, c.dono, "N2", agora(), c.gestor);
        // sem propor

        worker.processarDevidos();            // vencimento: sem proposta → no-op
        adiantar(c.org, "AUT_ESCALONAMENTO");
        worker.processarDevidos();            // escalonamento → ESCALADA

        assertEquals("ESCALADA", statusDelegacao(c.org, deleg));
        assertEquals("ENTRADA", statusPendencia(c.org, c.pend));
        assertEquals(0L, countOutbox(c.org, "delegacao.executada_por_ausencia"), "não executa");
        assertEquals(1L, countOutbox(c.org, "delegacao.escalada"));
        assertTrue(tipos(c.org, c.pend).contains("ESCALADA"));
    }

    // ---- Cenário: classe inelegível não aceita N2 -------------------------
    @Test
    void classe_inelegivel_nao_aceita_n2() {
        Ctx c = novo("DECISAO");
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "INSERT INTO classe_decisao (org_id, classe, nivel_maximo) VALUES (?, 'DECISAO', 'N3')")
                .setParameter(1, c.org.valor()).executeUpdate());

        given()
                .header("X-Org-Id", c.org.valor().toString())
                .header("X-Pessoa-Id", c.gestor.toString())
                .contentType("application/json")
                .body("{\"donoId\":\"" + c.dono + "\",\"nivel\":\"N2\",\"prazo\":\"" + agora() + "\"}")
        .when()
                .post("/v1/pendencias/" + c.pend + "/delegar")
        .then()
                .statusCode(422)
                .body("type", org.hamcrest.Matchers.containsString("alcada.inelegivel"));
    }

    // ---- Cenário: gestor ausente ------------------------------------------
    @Test
    void gestor_ausente() {
        Ctx c = novo("DECISAO");
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "INSERT INTO ausencia (org_id, pessoa_id, de, ate) VALUES (?, ?, now() - interval '1 day', now() + interval '1 day')")
                .setParameter(1, c.org.valor()).setParameter(2, c.gestor).executeUpdate());

        UUID deleg = motor.delegar(c.org, c.pend, c.dono, "N2", agora(), c.gestor);

        assertEquals("N3", nivelDelegacao(c.org, deleg), "N2 convertido para N3 por ausência");
        assertTrue(tipos(c.org, c.pend).contains("CONVERTIDA_POR_AUSENCIA"));
        assertEquals(1L, countOutbox(c.org, "delegacao.criada"), "executor notificado");
    }

    // ---- Cenário: reinício da aplicação -----------------------------------
    @Test
    void reinicio_da_aplicacao() {
        Ctx c = novo("DECISAO");
        UUID deleg = motor.delegar(c.org, c.pend, c.dono, "N2", agora(), c.gestor);
        motor.propor(c.org, deleg, "proposta", c.dono);
        worker.processarDevidos();  // → AGUARDANDO_JANELA (job VIRADA persistido)

        // "reinício": nenhum estado em memória; o worker recupera da tabela e executa 1x
        adiantar(c.org, "AUT_VIRADA");
        worker.processarDevidos();
        worker.processarDevidos();  // segunda passada não reexecuta

        assertEquals("EXECUTADA", statusDelegacao(c.org, deleg));
        assertEquals(1L, countOutbox(c.org, "delegacao.executada_por_ausencia"), "executado exatamente uma vez");
    }

    // ---- helpers -----------------------------------------------------------

    private record Ctx(OrgId org, UUID pend, UUID gestor, UUID dono, UUID executor) {
    }

    private Ctx novo(String classe) {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID pend = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("INSERT INTO organizacao (id, nome, sku) VALUES (?, 'Org', 'CLOUD')")
                    .setParameter(1, org.valor()).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status)
                    VALUES (?, ?, 'Aprovar algo', ?, 'SEMANA', 'ENTRADA')
                    """)
                    .setParameter(1, pend).setParameter(2, org.valor()).setParameter(3, classe).executeUpdate();
        });
        return new Ctx(org, pend, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private static OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static OffsetDateTime futuro() {
        // Mantém tempo útil disponível independentemente do horário em que a suíte roda.
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(2);
    }

    private void adiantar(OrgId org, String tipo) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "UPDATE job SET executar_em = now() WHERE org_id = ? AND tipo = ? AND status = 'AGENDADO'")
                .setParameter(1, org.valor()).setParameter(2, tipo).executeUpdate());
    }

    private String statusDelegacao(OrgId org, UUID id) {
        return (String) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT status FROM delegacao WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, id).getSingleResult());
    }

    private String nivelDelegacao(OrgId org, UUID id) {
        return (String) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT nivel FROM delegacao WHERE org_id = ? AND id = ?")
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
