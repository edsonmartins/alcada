package app.alcada.plataforma.trilha.internal;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/**
 * Rolagem de partição da trilha (ADR-0016). Garante que existam partições
 * mensais com folga à frente, para que nenhuma escrita caia na partição
 * {@code DEFAULT}. É manutenção idempotente — o {@code @Scheduled} apenas dá o
 * tick; a criação reusa {@code trilha_cria_particao(date)}, que não recria nem
 * falha se a partição já existe (mesma justificativa dos workers: não há estado
 * em memória).
 *
 * <p>Se a partição {@code DEFAULT} receber linhas, significa que a rolagem
 * atrasou — emite alerta.
 */
@ApplicationScoped
public class RolagemParticoes {

    private static final Logger LOG = Logger.getLogger(RolagemParticoes.class);

    /** Quantos meses à frente manter pré-criados. */
    static final int MESES_DE_FOLGA = 3;

    private final EntityManager em;

    public RolagemParticoes(EntityManager em) {
        this.em = em;
    }

    @Scheduled(cron = "0 0 3 * * ?", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        try {
            garantirJanela(MESES_DE_FOLGA);
            alertarSeDefaultPovoada();
        } catch (RuntimeException ex) {
            LOG.error("falha na rolagem de partições da trilha", ex);
        }
    }

    /** Cria as partições do mês corrente até +{@code meses} (idempotente). */
    @Transactional
    public void garantirJanela(int meses) {
        for (int i = 0; i <= meses; i++) {
            em.createNativeQuery(
                    "SELECT trilha_cria_particao((date_trunc('month', now()) + (?1 || ' month')::interval)::date)")
                    .setParameter(1, i)
                    .getSingleResult();
        }
    }

    /** Retorna quantas linhas caíram na partição DEFAULT (deveria ser sempre 0). */
    @Transactional
    public long linhasNaDefault() {
        Number n = (Number) em.createNativeQuery("SELECT count(*) FROM trilha_default").getSingleResult();
        return n.longValue();
    }

    private void alertarSeDefaultPovoada() {
        long linhas = linhasNaDefault();
        if (linhas > 0) {
            LOG.warnf("ALERTA: %d linha(s) na partição DEFAULT da trilha — rolagem atrasou", linhas);
        }
    }
}
