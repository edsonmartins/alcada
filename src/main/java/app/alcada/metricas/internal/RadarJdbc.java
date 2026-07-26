package app.alcada.metricas.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.alcada.metricas.port.Radar;
import app.alcada.metricas.port.RadarDados;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/**
 * Radar de gargalo — leitura pura sobre pendência/delegação/trilha. Todo
 * predicado carrega {@code org_id} (INV-15). Contagens honestas e separadas
 * (ADR-0024/0025). Nada é escrito.
 */
@ApplicationScoped
public class RadarJdbc implements Radar {

    private static final int SEMANAS = 8;

    // Estados ativos de uma delegação (a que vale agora).
    private static final String DELEG_ATIVA = "('ABERTA','PROPOSTA','AGUARDANDO_JANELA')";

    private final EntityManager em;
    private final app.alcada.plataforma.multitenancy.port.FusoTenant fuso;

    public RadarJdbc(EntityManager em, app.alcada.plataforma.multitenancy.port.FusoTenant fuso) {
        this.em = em;
        this.fuso = fuso;
    }

    @Override
    public RadarDados calcular(OrgId org) {
        var orgId = org.valor();
        ZoneId zona = fuso.fuso(org);

        long total = num(em.createNativeQuery(
                "SELECT count(*) FROM pendencia WHERE org_id = ? AND status <> 'FECHADA'")
                .setParameter(1, orgId).getSingleResult());

        long dependem = num(em.createNativeQuery("""
                SELECT count(*) FROM pendencia p WHERE p.org_id = ? AND (
                    p.status IN ('ENTRADA','AGENDADA')
                    OR (p.status = 'DELEGADA' AND EXISTS (
                        SELECT 1 FROM delegacao d WHERE d.org_id = p.org_id AND d.pendencia_id = p.id
                          AND d.status IN """ + DELEG_ATIVA + " AND d.nivel = 'N3')))")
                .setParameter(1, orgId).getSingleResult());

        long rodandoSemVoce = num(em.createNativeQuery("""
                SELECT count(*) FROM pendencia p WHERE p.org_id = ? AND p.status = 'DELEGADA'
                  AND EXISTS (SELECT 1 FROM delegacao d WHERE d.org_id = p.org_id AND d.pendencia_id = p.id
                        AND d.status IN """ + DELEG_ATIVA + " AND d.nivel IN ('N1','N2'))")
                .setParameter(1, orgId).getSingleResult());

        int pct = total == 0 ? 0 : (int) Math.round(dependem * 100.0 / total);

        return new RadarDados(
                new RadarDados.Dependencia(dependem, total, pct),
                rodandoSemVoce,
                adiados(orgId),
                piorEspera(orgId),
                autonomia(orgId),
                fechamentoCanal(orgId),
                encolhimento(orgId, zona),
                saudeGateway(orgId));
    }

    /** Saúde do gateway nos últimos 28 dias: chamadas, falhas (schema/indisp.), custo (018/009). */
    private RadarDados.SaudeGateway saudeGateway(java.util.UUID orgId) {
        Object[] r = (Object[]) em.createNativeQuery("""
                SELECT count(*),
                       count(*) FILTER (WHERE schema_ok = false),
                       coalesce(sum(custo), 0)
                FROM chamada_modelo
                WHERE org_id = ? AND ocorrido_em >= now() - interval '28 days'
                """).setParameter(1, orgId).getSingleResult();
        return new RadarDados.SaudeGateway(
                ((Number) r[0]).longValue(), ((Number) r[1]).longValue(), ((Number) r[2]).doubleValue());
    }

    private List<RadarDados.ItemAdiado> adiados(java.util.UUID orgId) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT id, titulo, adiado_count, o_que_trava, quem_espera, valor_em_jogo
                FROM pendencia
                WHERE org_id = ? AND adiado_count >= 3 AND status <> 'FECHADA'
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

    private RadarDados.PiorEspera piorEspera(java.util.UUID orgId) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT id, titulo, quem_espera,
                       floor(EXTRACT(EPOCH FROM (now() - criada_em)) / 86400)::bigint AS dias
                FROM pendencia WHERE org_id = ? AND status <> 'FECHADA'
                ORDER BY criada_em ASC LIMIT 1
                """).setParameter(1, orgId).getResultList();
        if (linhas.isEmpty()) {
            return null;
        }
        Object[] l = linhas.get(0);
        return new RadarDados.PiorEspera(l[0].toString(), (String) l[1],
                ((Number) l[3]).longValue(), (String) l[2]);
    }

    private RadarDados.Autonomia autonomia(java.util.UUID orgId) {
        Map<String, Long> c = contarTiposTrilha(orgId, 90,
                "'EXECUTADA','EXECUTADA_POR_AUSENCIA','DEVOLVIDA_PELO_EXECUTOR','ESCALADA','NIVEL_PROMOVIDO'");
        return new RadarDados.Autonomia(
                c.getOrDefault("EXECUTADA", 0L),
                c.getOrDefault("EXECUTADA_POR_AUSENCIA", 0L),
                c.getOrDefault("DEVOLVIDA_PELO_EXECUTOR", 0L),
                c.getOrDefault("ESCALADA", 0L),
                c.getOrDefault("NIVEL_PROMOVIDO", 0L));
    }

    private RadarDados.FechamentoCanal fechamentoCanal(java.util.UUID orgId) {
        Map<String, Long> c = contarTiposTrilha(orgId, 90,
                "'COMUNICADA','FALHA_COMUNICACAO','COMUNICACAO_IMPOSSIVEL'");
        return new RadarDados.FechamentoCanal(
                c.getOrDefault("COMUNICADA", 0L),
                c.getOrDefault("FALHA_COMUNICACAO", 0L),
                c.getOrDefault("COMUNICACAO_IMPOSSIVEL", 0L));
    }

    /** Conta eventos de trilha por tipo numa janela de dias. Predicado com org_id. */
    private Map<String, Long> contarTiposTrilha(java.util.UUID orgId, int dias, String tiposIn) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(
                "SELECT tipo, count(*) FROM trilha WHERE org_id = ?"
                        + " AND ocorrido_em >= now() - (? * interval '1 day')"
                        + " AND tipo IN (" + tiposIn + ") GROUP BY tipo")
                .setParameter(1, orgId).setParameter(2, dias).getResultList();
        Map<String, Long> m = new LinkedHashMap<>();
        for (Object[] l : linhas) {
            m.put((String) l[0], ((Number) l[1]).longValue());
        }
        return m;
    }

    /**
     * Série de 8 semanas (ancorada em segunda-feira, America/Sao_Paulo):
     * entraram (CAPTADA) × fecharam. Buckets vazios preenchidos com zero para a
     * linha do INV-01 nunca ter buracos.
     */
    private List<RadarDados.SemanaFluxo> encolhimento(java.util.UUID orgId, ZoneId zona) {
        // buckets de segunda, do mais antigo ao mais recente
        LocalDate segundaAtual = LocalDate.now(zona).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<String, long[]> buckets = new LinkedHashMap<>();
        for (int i = SEMANAS - 1; i >= 0; i--) {
            buckets.put(segundaAtual.minusWeeks(i).toString(), new long[] {0, 0});
        }

        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT to_char(date_trunc('week', (ocorrido_em AT TIME ZONE ?))::date, 'YYYY-MM-DD') AS semana,
                       count(*) FILTER (WHERE tipo = 'CAPTADA') AS entraram,
                       count(*) FILTER (WHERE tipo IN ('RESOLVIDA','EXECUTADA','EXECUTADA_POR_AUSENCIA','DECIDIDA_NO_BLOCO')) AS fecharam
                FROM trilha
                WHERE org_id = ? AND ocorrido_em >= now() - (? * interval '1 day')
                GROUP BY 1
                """).setParameter(1, zona.getId()).setParameter(2, orgId)
                .setParameter(3, SEMANAS * 7).getResultList();
        for (Object[] l : linhas) {
            long[] b = buckets.get((String) l[0]);
            if (b != null) {
                b[0] = ((Number) l[1]).longValue();
                b[1] = ((Number) l[2]).longValue();
            }
        }

        List<RadarDados.SemanaFluxo> serie = new ArrayList<>(SEMANAS);
        for (Map.Entry<String, long[]> e : buckets.entrySet()) {
            serie.add(new RadarDados.SemanaFluxo(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }
        return serie;
    }

    private static long num(Object o) {
        return ((Number) o).longValue();
    }
}
