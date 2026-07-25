package app.alcada.metricas.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import app.alcada.metricas.port.RadarDados;
import app.alcada.metricas.port.RevisaoDados;
import app.alcada.metricas.port.RevisaoSemanal;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/**
 * Roteiro da revisão de sexta — leitura pura. Semana corrente ancorada em
 * segunda-feira, America/Sao_Paulo. Todo predicado carrega {@code org_id}.
 */
@ApplicationScoped
public class RevisaoJdbc implements RevisaoSemanal {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final int LIMITE_ENTRADA = 50;

    private final EntityManager em;

    public RevisaoJdbc(EntityManager em) {
        this.em = em;
    }

    @Override
    public RevisaoDados calcular(OrgId org) {
        UUID orgId = org.valor();
        return new RevisaoDados(entrada(orgId), adiados(orgId), podeVirarRegra(orgId), resumoSemana(orgId));
    }

    private RevisaoDados.Entrada entrada(UUID orgId) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT id, titulo, quem_espera FROM pendencia
                WHERE org_id = ? AND status = 'ENTRADA'
                ORDER BY criada_em DESC LIMIT ?
                """).setParameter(1, orgId).setParameter(2, LIMITE_ENTRADA).getResultList();
        List<RevisaoDados.ItemFila> itens = new ArrayList<>(linhas.size());
        for (Object[] l : linhas) {
            itens.add(new RevisaoDados.ItemFila(l[0].toString(), (String) l[1], (String) l[2]));
        }
        long qtd = num(em.createNativeQuery(
                "SELECT count(*) FROM pendencia WHERE org_id = ? AND status = 'ENTRADA'")
                .setParameter(1, orgId).getSingleResult());
        return new RevisaoDados.Entrada(qtd, itens);
    }

    private List<RadarDados.ItemAdiado> adiados(UUID orgId) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT id, titulo, adiado_count, o_que_trava, quem_espera, valor_em_jogo
                FROM pendencia WHERE org_id = ? AND adiado_count >= 3 AND status <> 'FECHADA'
                ORDER BY adiado_count DESC, criada_em ASC
                """).setParameter(1, orgId).getResultList();
        List<RadarDados.ItemAdiado> res = new ArrayList<>(linhas.size());
        for (Object[] l : linhas) {
            res.add(new RadarDados.ItemAdiado(l[0].toString(), (String) l[1],
                    ((Number) l[2]).intValue(), (String) l[3], (String) l[4],
                    l[5] == null ? null : ((Number) l[5]).doubleValue()));
        }
        return res;
    }

    /** DICA (não regra): assinatura {classe} com ≥3 RESOLVIDA em 28 dias (RFC-0003). */
    private List<RevisaoDados.DicaRegra> podeVirarRegra(UUID orgId) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT p.classe, count(*) AS n
                FROM trilha t JOIN pendencia p ON p.id = t.pendencia_id AND p.org_id = t.org_id
                WHERE t.org_id = ? AND t.tipo = 'RESOLVIDA' AND t.ocorrido_em >= now() - interval '28 days'
                GROUP BY p.classe HAVING count(*) >= 3 ORDER BY n DESC
                """).setParameter(1, orgId).getResultList();
        List<RevisaoDados.DicaRegra> res = new ArrayList<>(linhas.size());
        for (Object[] l : linhas) {
            res.add(new RevisaoDados.DicaRegra((String) l[0], ((Number) l[1]).longValue()));
        }
        return res;
    }

    private RevisaoDados.ResumoSemana resumoSemana(UUID orgId) {
        OffsetDateTime inicio = LocalDate.now(SP)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(SP).toOffsetDateTime();
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(
                "SELECT tipo, count(*) FROM trilha WHERE org_id = ? AND ocorrido_em >= ? GROUP BY tipo")
                .setParameter(1, orgId).setParameter(2, inicio).getResultList();
        Map<String, Long> c = new LinkedHashMap<>();
        for (Object[] l : linhas) {
            c.put((String) l[0], ((Number) l[1]).longValue());
        }
        long resolvidas = c.getOrDefault("RESOLVIDA", 0L);
        long executadas = c.getOrDefault("EXECUTADA", 0L) + c.getOrDefault("EXECUTADA_POR_AUSENCIA", 0L);
        long decididasBloco = c.getOrDefault("DECIDIDA_NO_BLOCO", 0L);
        return new RevisaoDados.ResumoSemana(
                resolvidas,
                executadas,
                c.getOrDefault("REPASSADA", 0L),
                c.getOrDefault("ESCALADA", 0L),
                c.getOrDefault("DEVOLVIDA_PELO_EXECUTOR", 0L),
                resolvidas + executadas + decididasBloco);
    }

    private static long num(Object o) {
        return ((Number) o).longValue();
    }
}
