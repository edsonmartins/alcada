package app.alcada.plataforma.gateway.internal;

import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.gateway.port.ReprocessadorExtracao;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/**
 * Worker da fila de reprocesso. O {@code @Scheduled} é só o tick; o estado vive
 * na tabela. Reserva com {@code FOR UPDATE SKIP LOCKED} e delega à porta
 * {@link ReprocessadorExtracao} (implementada por captura/001). Sem essa porta
 * (esqueleto), não há o que reprocessar e o lote é no-op.
 */
@ApplicationScoped
public class WorkerReprocesso {

    private static final Logger LOG = Logger.getLogger(WorkerReprocesso.class);
    private static final int LOTE = 50;
    private static final int MAX_TENTATIVAS = 8;

    private static final String CLAIM = """
            SELECT id, org_id, ref_mensagem_id, tentativas
            FROM tarefa_reprocesso
            WHERE status = 'PENDENTE' AND disponivel_em <= now()
            ORDER BY disponivel_em
            FOR UPDATE SKIP LOCKED
            LIMIT ?
            """;

    private static final String MARCA_CONCLUIDO = """
            UPDATE tarefa_reprocesso SET status = 'CONCLUIDO'
            WHERE id = ? AND org_id = ?
            """;

    private static final String MARCA_FALHA = """
            UPDATE tarefa_reprocesso
            SET tentativas = ?, ultimo_erro = ?,
                status = CASE WHEN ? >= ? THEN 'ERRO' ELSE 'PENDENTE' END,
                disponivel_em = now() + (? * interval '1 second')
            WHERE id = ? AND org_id = ?
            """;

    private final EntityManager em;
    private final Instance<ReprocessadorExtracao> reprocessador;

    public WorkerReprocesso(EntityManager em, Instance<ReprocessadorExtracao> reprocessador) {
        this.em = em;
        this.reprocessador = reprocessador;
    }

    @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        try {
            processarLote();
        } catch (RuntimeException ex) {
            LOG.error("falha no lote de reprocesso", ex);
        }
    }

    @Transactional
    public int processarLote() {
        if (reprocessador.isUnsatisfied()) {
            return 0; // captura (001) ainda não forneceu o reprocessador
        }
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(CLAIM).setParameter(1, LOTE).getResultList();

        int concluidos = 0;
        for (Object[] l : linhas) {
            UUID id = (UUID) l[0];
            UUID org = (UUID) l[1];
            UUID ref = (UUID) l[2];
            int tentativas = ((Number) l[3]).intValue();
            try {
                reprocessador.get().reprocessar(new OrgId(org), ref);
                em.createNativeQuery(MARCA_CONCLUIDO).setParameter(1, id).setParameter(2, org).executeUpdate();
                concluidos++;
            } catch (RuntimeException falha) {
                int novas = tentativas + 1;
                long backoff = (long) Math.pow(2, Math.min(novas, 10));
                em.createNativeQuery(MARCA_FALHA)
                        .setParameter(1, novas).setParameter(2, falha.getMessage())
                        .setParameter(3, novas).setParameter(4, MAX_TENTATIVAS)
                        .setParameter(5, backoff).setParameter(6, id).setParameter(7, org)
                        .executeUpdate();
            }
        }
        return concluidos;
    }
}
