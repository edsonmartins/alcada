package app.alcada.plataforma.scheduler.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.scheduler.port.ExecutorJob;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/**
 * Worker do scheduler persistente. O {@code @Scheduled} é só o tick; o que
 * executar e quando está na tabela {@code job}. Reserva devidos com
 * {@code FOR UPDATE SKIP LOCKED}, executa via {@link ExecutorJob} roteado por
 * tipo e marca {@code CONCLUIDO} — tudo na mesma transação, então um reinício
 * no meio não perde nem duplica: a linha volta a {@code AGENDADO} intacta.
 */
@ApplicationScoped
public class WorkerJobs {

    private static final Logger LOG = Logger.getLogger(WorkerJobs.class);
    private static final int LOTE = 50;
    private static final int MAX_TENTATIVAS = 8;

    private static final String CLAIM = """
            SELECT id, org_id, tipo, chave, payload::text, tentativas
            FROM job
            WHERE status = 'AGENDADO' AND executar_em <= now()
            ORDER BY executar_em
            FOR UPDATE SKIP LOCKED
            LIMIT ?
            """;

    private static final String MARCA_CONCLUIDO = """
            UPDATE job SET status = 'CONCLUIDO', concluido_em = now()
            WHERE id = ? AND org_id = ?
            """;

    private static final String MARCA_FALHA = """
            UPDATE job
            SET tentativas = ?, ultimo_erro = ?,
                status = CASE WHEN ? >= ? THEN 'ERRO' ELSE 'AGENDADO' END,
                proxima_tentativa = now() + (? * interval '1 second'),
                executar_em = now() + (? * interval '1 second')
            WHERE id = ? AND org_id = ?
            """;

    private final EntityManager em;
    private final Instance<ExecutorJob> executores;

    public WorkerJobs(EntityManager em, Instance<ExecutorJob> executores) {
        this.em = em;
        this.executores = executores;
    }

    @Scheduled(every = "2s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        try {
            processarDevidos();
        } catch (RuntimeException ex) {
            LOG.error("falha no lote de jobs", ex);
        }
    }

    /** Reserva e processa os jobs devidos. Retorna quantos concluíram. */
    @Transactional
    public int processarDevidos() {
        Map<String, ExecutorJob> porTipo = new HashMap<>();
        for (ExecutorJob e : executores) {
            porTipo.put(e.tipo(), e);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(CLAIM)
                .setParameter(1, LOTE)
                .getResultList();

        int concluidos = 0;
        for (Object[] l : linhas) {
            UUID id = (UUID) l[0];
            UUID org = (UUID) l[1];
            String tipo = (String) l[2];
            String chave = (String) l[3];
            String payload = (String) l[4];
            int tentativas = ((Number) l[5]).intValue();

            ExecutorJob exec = porTipo.get(tipo);
            if (exec == null) {
                LOG.warnf("sem executor para tipo de job '%s' (id=%s)", tipo, id);
                continue; // permanece AGENDADO; outro nó/versão pode ter o executor
            }
            try {
                exec.executar(new OrgId(org), chave, payload);
                em.createNativeQuery(MARCA_CONCLUIDO)
                        .setParameter(1, id).setParameter(2, org)
                        .executeUpdate();
                concluidos++;
            } catch (RuntimeException falha) {
                int novas = tentativas + 1;
                long backoff = (long) Math.pow(2, Math.min(novas, 10));
                em.createNativeQuery(MARCA_FALHA)
                        .setParameter(1, novas)
                        .setParameter(2, falha.getMessage())
                        .setParameter(3, novas)
                        .setParameter(4, MAX_TENTATIVAS)
                        .setParameter(5, backoff)
                        .setParameter(6, backoff)
                        .setParameter(7, id)
                        .setParameter(8, org)
                        .executeUpdate();
            }
        }
        return concluidos;
    }
}
