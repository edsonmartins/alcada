package app.alcada.plataforma.outbox.internal;

import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.port.Despachante;
import app.alcada.plataforma.outbox.port.MensagemOutbox;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/**
 * Worker do outbox. O {@code @Scheduled} dá apenas o <em>tick</em> do poll — o
 * estado (o que entregar) vive na tabela, nunca em memória (CLAUDE.md §4).
 *
 * <p>Reserva lotes com {@code FOR UPDATE SKIP LOCKED} dentro de uma transação;
 * entrega via {@link Despachante} (ao menos uma vez) e marca {@code ENVIADO}.
 * Falha por mensagem aplica backoff exponencial sem derrubar o lote.
 */
@ApplicationScoped
public class WorkerOutbox {

    private static final Logger LOG = Logger.getLogger(WorkerOutbox.class);
    private static final int LOTE = 50;
    private static final int MAX_TENTATIVAS = 8;

    private static final String CLAIM = """
            SELECT id, org_id, tipo, payload::text, idempotency_key, tentativas
            FROM outbox
            WHERE status = 'PENDENTE' AND disponivel_em <= now()
            ORDER BY disponivel_em
            FOR UPDATE SKIP LOCKED
            LIMIT ?
            """;

    private static final String MARCA_ENVIADO = """
            UPDATE outbox SET status = 'ENVIADO', enviado_em = now()
            WHERE id = ? AND org_id = ?
            """;

    private static final String MARCA_FALHA = """
            UPDATE outbox
            SET tentativas = ?, ultimo_erro = ?,
                status = CASE WHEN ? >= ? THEN 'ERRO' ELSE 'PENDENTE' END,
                disponivel_em = now() + (? * interval '1 second')
            WHERE id = ? AND org_id = ?
            """;

    private final EntityManager em;
    private final Instance<Despachante> despachante;

    public WorkerOutbox(EntityManager em, Instance<Despachante> despachante) {
        this.em = em;
        this.despachante = despachante;
    }

    @Scheduled(every = "2s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        try {
            processarLote();
        } catch (RuntimeException ex) {
            LOG.error("falha no lote do outbox", ex);
        }
    }

    /** Reserva e processa um lote. Retorna quantas mensagens foram entregues. */
    @Transactional
    public int processarLote() {
        if (despachante.isUnsatisfied()) {
            return 0; // sem destino registrado ainda (esqueleto)
        }
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(CLAIM)
                .setParameter(1, LOTE)
                .getResultList();

        int entregues = 0;
        for (Object[] l : linhas) {
            UUID id = (UUID) l[0];
            UUID org = (UUID) l[1];
            int tentativas = ((Number) l[5]).intValue();
            MensagemOutbox msg = new MensagemOutbox(
                    new OrgId(org), (String) l[2], (String) l[3], (String) l[4]);
            try {
                despachante.get().entregar(msg);
                em.createNativeQuery(MARCA_ENVIADO)
                        .setParameter(1, id).setParameter(2, org)
                        .executeUpdate();
                entregues++;
            } catch (RuntimeException falha) {
                int novas = tentativas + 1;
                long backoff = (long) Math.pow(2, Math.min(novas, 10)); // segundos
                em.createNativeQuery(MARCA_FALHA)
                        .setParameter(1, novas)
                        .setParameter(2, falha.getMessage())
                        .setParameter(3, novas)
                        .setParameter(4, MAX_TENTATIVAS)
                        .setParameter(5, backoff)
                        .setParameter(6, id)
                        .setParameter(7, org)
                        .executeUpdate();
            }
        }
        return entregues;
    }
}
