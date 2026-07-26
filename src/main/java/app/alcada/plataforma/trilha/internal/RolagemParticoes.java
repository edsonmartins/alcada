package app.alcada.plataforma.trilha.internal;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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

    /** Janela de retenção quente; além disso a partição é destacada (frio). 0 = nunca. */
    @ConfigProperty(name = "trilha.retencao-meses", defaultValue = "24")
    int retencaoMeses;

    private final EntityManager em;

    public RolagemParticoes(EntityManager em) {
        this.em = em;
    }

    @Scheduled(cron = "0 0 3 * * ?", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        try {
            garantirJanela(MESES_DE_FOLGA);
            alertarSeDefaultPovoada();
            if (retencaoMeses > 0) {
                int frias = arquivarFrias(retencaoMeses);
                if (frias > 0) {
                    LOG.infof("arquivamento frio da trilha: %d partição(ões) destacada(s)", frias);
                }
            }
        } catch (RuntimeException ex) {
            LOG.error("falha na rolagem de partições da trilha", ex);
        }
    }

    /**
     * Arquivamento frio (004): destaca (não deleta) as partições mensais além da
     * retenção. Imutável (ADR-0016) — a tabela destacada persiste, só sai do
     * caminho quente. Retorna quantas foram destacadas.
     */
    @Transactional
    public int arquivarFrias(int meses) {
        Number n = (Number) em.createNativeQuery("SELECT trilha_arquiva_frias(?1)")
                .setParameter(1, meses).getSingleResult();
        return n.intValue();
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
