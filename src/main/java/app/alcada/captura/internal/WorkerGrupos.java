package app.alcada.captura.internal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Varredura por JANELA dos grupos acompanhados (024, F2). Sem timer em memória
 * (CLAUDE.md §4): o {@code @Scheduled} é só o tick; o estado do debounce mora em
 * {@code grupo_acompanhado} (ultimo_visto / avaliado_em). A cada tick reserva os
 * grupos cuja conversa já "assentou" (última mensagem mais velha que o debounce)
 * e que têm conteúdo novo desde a última avaliação, com {@code FOR UPDATE SKIP
 * LOCKED}, marca {@code avaliado_em} na mesma transação (não re-processa em loop),
 * e então extrai o compromisso da janela via {@link ProcessadorGrupo}.
 *
 * <p>A reserva e o processamento ficam em transações separadas de propósito: a
 * chamada ao modelo (dentro de {@code processar}) não segura o lock de reserva do
 * lote inteiro, e uma falha em um grupo não desfaz a reserva dos outros.
 */
@ApplicationScoped
public class WorkerGrupos {

    private static final Logger LOG = Logger.getLogger(WorkerGrupos.class);
    private static final int LOTE = 50;

    @ConfigProperty(name = "grupos.debounce-segundos", defaultValue = "90")
    long debounceSegundos;

    private static final String RESERVAR = """
            SELECT org_id, grupo_id FROM grupo_acompanhado
            WHERE ativa
              AND (avaliado_em IS NULL OR ultimo_visto > avaliado_em)
              AND (ultimo_visto <= now() - (? * interval '1 second')
                   OR (mencao_em IS NOT NULL AND (avaliado_em IS NULL OR mencao_em > avaliado_em)))
            ORDER BY ultimo_visto
            FOR UPDATE SKIP LOCKED
            LIMIT ?
            """;

    private final EntityManager em;
    private final ProcessadorGrupo processador;

    public WorkerGrupos(EntityManager em, ProcessadorGrupo processador) {
        this.em = em;
        this.processador = processador;
    }

    @Scheduled(every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        try {
            varrer();
        } catch (RuntimeException ex) {
            LOG.error("falha na varredura de grupos", ex);
        }
    }

    /** Reserva os grupos assentados e extrai a janela de cada um. Retorna quantos. */
    public int varrer() {
        List<Reserva> reservas = QuarkusTransaction.requiringNew().call(this::reservar);
        for (Reserva r : reservas) {
            try {
                processador.processar(r.org(), r.grupoId());
            } catch (RuntimeException ex) {
                LOG.errorf(ex, "falha ao processar janela do grupo (org=%s)", r.org().valor());
            }
        }
        return reservas.size();
    }

    private record Reserva(OrgId org, String grupoId) {
    }

    /** Reserva o lote e avança {@code avaliado_em} na mesma transação (debounce). */
    private List<Reserva> reservar() {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(RESERVAR)
                .setParameter(1, debounceSegundos)
                .setParameter(2, LOTE)
                .getResultList();

        // Dedup por (org, grupo): o mesmo chat_jid pode existir sob mais de uma fonte
        // na org (unique é por fonte_id,grupo_id). A janela é agregada por
        // (org, thread_ref), então processa-se UMA vez por grupo — senão a 2ª rodada
        // trataria a pendência recém-criada como existente e lançaria cobrança falsa.
        List<Reserva> reservas = new ArrayList<>(linhas.size());
        Set<String> vistos = new HashSet<>();
        for (Object[] l : linhas) {
            OrgId org = new OrgId((UUID) l[0]);
            String grupoId = (String) l[1];
            if (!vistos.add(org.valor() + "|" + grupoId)) {
                continue; // já reservado neste lote (outra fonte, mesmo grupo)
            }
            // O UPDATE por (org, grupo) já avança avaliado_em de todas as fontes do grupo.
            em.createNativeQuery("""
                    UPDATE grupo_acompanhado SET avaliado_em = now()
                    WHERE org_id = ? AND grupo_id = ?
                    """).setParameter(1, org.valor()).setParameter(2, grupoId).executeUpdate();
            reservas.add(new Reserva(org, grupoId));
        }
        return reservas;
    }
}
