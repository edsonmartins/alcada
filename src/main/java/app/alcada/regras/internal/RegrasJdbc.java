package app.alcada.regras.internal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.regras.port.Mineracao;
import app.alcada.regras.port.PropostaRegra;
import app.alcada.regras.port.RegraAtiva;
import app.alcada.regras.port.Regras;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Mineração determinística de regra de autonomia (RFC-0003 §A) e comandos sobre
 * {@code regra_autonomia}. Leitura pura na mineração; escritas simples nos
 * comandos. Todo predicado carrega {@code org_id} (INV-15). Sem modelo (INV-10).
 */
@ApplicationScoped
public class RegrasJdbc implements Mineracao, Regras {

    // Desfecho deliberado (a decisão saiu) e reversões (sinal negativo do RFC).
    private static final String DESFECHO = "'RESOLVIDA','EXECUTADA','EXECUTADA_POR_AUSENCIA','DECIDIDA_NO_BLOCO'";
    private static final String REVERSAO = "'INTERROMPIDA','DESFEITA_NA_JANELA','DEVOLVIDA_PELO_EXECUTOR','ESCALADA'";
    private static final double CONSISTENCIA_MIN = 0.95;
    private static final int MAX_CASOS = 20;

    private final EntityManager em;
    private final long minOcorrencias;

    public RegrasJdbc(EntityManager em,
                      @ConfigProperty(name = "mineracao.min-ocorrencias", defaultValue = "15") long minOcorrencias) {
        this.em = em;
        this.minOcorrencias = minOcorrencias;
    }

    // ---- Mineração -----------------------------------------------------------

    @Override
    public List<PropostaRegra> propostas(OrgId org) {
        UUID orgId = org.valor();
        Set<String> silenciadas = silenciadas(orgId);
        Set<String> comRegra = classesComRegraAtiva(orgId);

        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT x.classe,
                       count(*) FILTER (WHERE x.tem_desfecho) AS ocorrencias,
                       count(*) FILTER (WHERE x.tem_desfecho AND x.revertido) AS revertidas
                FROM (
                  SELECT p.id, p.classe,
                    EXISTS(SELECT 1 FROM trilha t WHERE t.org_id = p.org_id AND t.pendencia_id = p.id
                           AND t.tipo IN (""" + DESFECHO + """
                           ) AND t.ocorrido_em >= now() - interval '90 days') AS tem_desfecho,
                    EXISTS(SELECT 1 FROM trilha t WHERE t.org_id = p.org_id AND t.pendencia_id = p.id
                           AND t.tipo IN (""" + REVERSAO + """
                           )) AS revertido
                  FROM pendencia p WHERE p.org_id = ?
                ) x
                GROUP BY x.classe
                """).setParameter(1, orgId).getResultList();

        List<PropostaRegra> propostas = new ArrayList<>();
        for (Object[] l : linhas) {
            String classe = (String) l[0];
            long ocorrencias = ((Number) l[1]).longValue();
            long revertidas = ((Number) l[2]).longValue();
            if (ocorrencias < minOcorrencias || revertidas > 0) {
                continue; // ruído não vira política; zero reversões é condição dura
            }
            double consistencia = (ocorrencias - revertidas) / (double) ocorrencias;
            if (consistencia < CONSISTENCIA_MIN) {
                continue;
            }
            if (silenciadas.contains(classe) || comRegra.contains(classe)) {
                continue;
            }
            propostas.add(new PropostaRegra(classe, ocorrencias, consistencia,
                    "N1", donoSugerido(orgId, classe), casos(orgId, classe)));
        }
        return propostas;
    }

    private Set<String> silenciadas(UUID orgId) {
        @SuppressWarnings("unchecked")
        List<String> l = em.createNativeQuery("SELECT classe FROM regra_silenciada WHERE org_id = ?")
                .setParameter(1, orgId).getResultList();
        return new HashSet<>(l);
    }

    private Set<String> classesComRegraAtiva(UUID orgId) {
        @SuppressWarnings("unchecked")
        List<String> l = em.createNativeQuery(
                "SELECT DISTINCT classe FROM regra_autonomia WHERE org_id = ? AND ativa")
                .setParameter(1, orgId).getResultList();
        return new HashSet<>(l);
    }

    private String donoSugerido(UUID orgId, String classe) {
        @SuppressWarnings("unchecked")
        List<Object> l = em.createNativeQuery("""
                SELECT d.dono_id FROM delegacao d
                JOIN pendencia p ON p.id = d.pendencia_id AND p.org_id = d.org_id
                WHERE d.org_id = ? AND p.classe = ?
                GROUP BY d.dono_id ORDER BY count(*) DESC LIMIT 1
                """).setParameter(1, orgId).setParameter(2, classe).getResultList();
        return l.isEmpty() || l.get(0) == null ? null : l.get(0).toString();
    }

    private List<PropostaRegra.Caso> casos(UUID orgId, String classe) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT p.id, p.titulo, p.valor_em_jogo,
                       (SELECT t.tipo FROM trilha t WHERE t.org_id = p.org_id AND t.pendencia_id = p.id
                          AND t.tipo IN (""" + DESFECHO + """
                          ) ORDER BY t.ocorrido_em DESC LIMIT 1) AS desfecho
                FROM pendencia p
                WHERE p.org_id = ? AND p.classe = ?
                  AND EXISTS(SELECT 1 FROM trilha t WHERE t.org_id = p.org_id AND t.pendencia_id = p.id
                        AND t.tipo IN (""" + DESFECHO + """
                        ) AND t.ocorrido_em >= now() - interval '90 days')
                ORDER BY p.criada_em DESC LIMIT ?
                """).setParameter(1, orgId).setParameter(2, classe).setParameter(3, MAX_CASOS).getResultList();
        List<PropostaRegra.Caso> casos = new ArrayList<>(linhas.size());
        for (Object[] l : linhas) {
            casos.add(new PropostaRegra.Caso(l[0].toString(), (String) l[1],
                    (String) l[3], l[2] == null ? null : ((Number) l[2]).doubleValue()));
        }
        return casos;
    }

    // ---- Comandos ------------------------------------------------------------

    @Override
    public List<RegraAtiva> ativas(OrgId org) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT id, classe, nivel, dono_id, criada_em
                FROM regra_autonomia WHERE org_id = ? AND ativa ORDER BY criada_em DESC
                """).setParameter(1, org.valor()).getResultList();
        List<RegraAtiva> res = new ArrayList<>(linhas.size());
        for (Object[] l : linhas) {
            res.add(new RegraAtiva(l[0].toString(), (String) l[1], (String) l[2],
                    l[3].toString(), toOdt(l[4])));
        }
        return res;
    }

    @Override
    public boolean existeRegraAtiva(OrgId org, String classe) {
        long n = ((Number) em.createNativeQuery(
                "SELECT count(*) FROM regra_autonomia WHERE org_id = ? AND classe = ? AND ativa")
                .setParameter(1, org.valor()).setParameter(2, classe).getSingleResult()).longValue();
        return n > 0;
    }

    @Override
    public String nivelMaximo(OrgId org, String classe) {
        try {
            Object v = em.createNativeQuery(
                    "SELECT nivel_maximo FROM classe_decisao WHERE org_id = ? AND classe = ?")
                    .setParameter(1, org.valor()).setParameter(2, classe).getSingleResult();
            return (String) v;
        } catch (NoResultException e) {
            return null;
        }
    }

    @Override
    public UUID criar(OrgId org, String classe, String nivel, UUID donoId) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO regra_autonomia (id, org_id, classe, nivel, dono_id, ativa)
                VALUES (?, ?, ?, ?, ?, true)
                """).setParameter(1, id).setParameter(2, org.valor()).setParameter(3, classe)
                .setParameter(4, nivel).setParameter(5, donoId).executeUpdate();
        return id;
    }

    @Override
    public void silenciar(OrgId org, String classe, UUID por) {
        em.createNativeQuery("""
                INSERT INTO regra_silenciada (org_id, classe, por) VALUES (?, ?, ?)
                ON CONFLICT (org_id, classe) DO NOTHING
                """).setParameter(1, org.valor()).setParameter(2, classe).setParameter(3, por).executeUpdate();
    }

    @Override
    public void desativar(OrgId org, UUID regraId) {
        em.createNativeQuery("UPDATE regra_autonomia SET ativa = false WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, regraId).executeUpdate();
    }

    private static OffsetDateTime toOdt(Object v) {
        if (v instanceof OffsetDateTime odt) {
            return odt;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (v instanceof java.time.Instant inst) {
            return inst.atOffset(ZoneOffset.UTC);
        }
        return null;
    }
}
