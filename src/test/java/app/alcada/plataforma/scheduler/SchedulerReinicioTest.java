package app.alcada.plataforma.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.scheduler.internal.WorkerJobs;
import app.alcada.plataforma.scheduler.port.Agenda;
import app.alcada.plataforma.scheduler.port.TarefaAgendada;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Scheduler persistente (CLAUDE.md §4). O estado do job vive na tabela; nenhum
 * timer em memória. Um "reinício" é simulado processando com o worker sem
 * qualquer estado prévio na aplicação — a linha persistida basta.
 */
@QuarkusTest
class SchedulerReinicioTest {

    @Inject Agenda agenda;
    @Inject WorkerJobs worker;
    @Inject ExecutorFake executor;
    @Inject EntityManager em;

    private OrgId org;

    @BeforeEach
    void setup() {
        org = new OrgId(UUID.randomUUID());
        executor.limpar();
    }

    @Test
    void job_agendado_sobrevive_reinicio_e_executa_uma_vez() {
        String chave = "delegacao-" + UUID.randomUUID() + ":virada";
        // Agendado para o passado: já está devido.
        OffsetDateTime ontem = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        QuarkusTransaction.requiringNew().run(() ->
                agenda.agendar(new TarefaAgendada(org, ExecutorFake.TIPO, chave, ontem, "{}")));

        // "Reinício": o worker não tem estado em memória; recupera da tabela.
        worker.processarDevidos();
        assertEquals(1, executor.execucoes(chave), "executou exatamente uma vez");

        // Segunda passada não reexecuta (job CONCLUIDO).
        worker.processarDevidos();
        assertEquals(1, executor.execucoes(chave), "não duplica execução");
        assertEquals("CONCLUIDO", statusJob(chave));
    }

    @Test
    void agendar_a_mesma_chave_duas_vezes_nao_duplica() {
        String chave = "delegacao-" + UUID.randomUUID() + ":escalonamento";
        OffsetDateTime futuro = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        QuarkusTransaction.requiringNew().run(() ->
                agenda.agendar(new TarefaAgendada(org, ExecutorFake.TIPO, chave, futuro, "{}")));
        QuarkusTransaction.requiringNew().run(() ->
                agenda.agendar(new TarefaAgendada(org, ExecutorFake.TIPO, chave, futuro, "{}")));

        assertEquals(1, contarJobs(chave), "idempotente por (tipo, chave)");
    }

    private long contarJobs(String chave) {
        return ((Number) em.createNativeQuery(
                "select count(*) from job where chave = ? and org_id = ?")
                .setParameter(1, chave).setParameter(2, org.valor())
                .getSingleResult()).longValue();
    }

    private String statusJob(String chave) {
        return (String) em.createNativeQuery(
                "select status from job where chave = ? and org_id = ?")
                .setParameter(1, chave).setParameter(2, org.valor())
                .getSingleResult();
    }
}
