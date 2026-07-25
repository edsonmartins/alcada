package app.alcada.esteira.internal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import app.alcada.esteira.port.AvaliacaoResultado;
import app.alcada.esteira.port.Avaliacoes;
import app.alcada.esteira.port.ChecklistDados;
import app.alcada.esteira.port.EntradasEsteira.ApontamentoItem;
import app.alcada.esteira.port.EntradasEsteira.NovaEtapa;
import app.alcada.esteira.port.EntradasEsteira.NovoCriterio;
import app.alcada.esteira.port.EntradasEsteira.ResultadoItem;
import app.alcada.esteira.port.EsteiraDados;
import app.alcada.esteira.port.Esteiras;
import app.alcada.esteira.port.InstanciaDados;
import app.alcada.esteira.port.MineracaoChecklist;
import app.alcada.esteira.port.PropostaChecklist;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Agregado esteira + avaliação + mineração de checklist (§B). Predicados com
 * {@code org_id} (INV-15). A pendência gerada por reprovação/julgamento entra na
 * fila como classe ESTEIRA, com o resultado anexado. Mineração é leitura pura.
 */
@ApplicationScoped
public class EsteiraJdbc implements Esteiras, Avaliacoes, MineracaoChecklist {

    private final EntityManager em;
    private final Trilha trilha;
    private final int minReprovacoes;

    public EsteiraJdbc(EntityManager em, Trilha trilha,
                       @ConfigProperty(name = "esteira.checklist.min-reprovacoes", defaultValue = "4") int minReprovacoes) {
        this.em = em;
        this.trilha = trilha;
        this.minReprovacoes = minReprovacoes;
    }

    // ---- montagem ------------------------------------------------------------

    @Override
    public UUID criar(OrgId org, String nome, List<NovaEtapa> etapas) {
        UUID esteiraId = UUID.randomUUID();
        em.createNativeQuery("INSERT INTO esteira (id, org_id, nome) VALUES (?, ?, ?)")
                .setParameter(1, esteiraId).setParameter(2, org.valor()).setParameter(3, nome).executeUpdate();
        for (NovaEtapa e : etapas) {
            UUID etapaId = UUID.randomUUID();
            em.createNativeQuery("""
                    INSERT INTO etapa (id, org_id, esteira_id, ordem, nome, dono_id, sla, etapa_do_gestor)
                    VALUES (?, ?, ?, ?, ?, ?, CAST(NULLIF(?, '') AS interval), ?)
                    """).setParameter(1, etapaId).setParameter(2, org.valor()).setParameter(3, esteiraId)
                    .setParameter(4, e.ordem()).setParameter(5, e.nome())
                    .setParameter(6, e.donoId() == null ? null : UUID.fromString(e.donoId()))
                    .setParameter(7, e.sla() == null ? "" : e.sla()).setParameter(8, e.etapaDoGestor())
                    .executeUpdate();
            if (e.etapaDoGestor()) {
                criarChecklist(org, etapaId, 1); // versão inicial vazia
            }
        }
        return esteiraId;
    }

    @Override
    public List<EsteiraDados> listar(OrgId org) {
        @SuppressWarnings("unchecked")
        List<Object[]> es = em.createNativeQuery(
                "SELECT id, nome FROM esteira WHERE org_id = ? ORDER BY criada_em")
                .setParameter(1, org.valor()).getResultList();
        List<EsteiraDados> res = new ArrayList<>(es.size());
        for (Object[] e : es) {
            UUID esteiraId = (UUID) e[0];
            @SuppressWarnings("unchecked")
            List<Object[]> ets = em.createNativeQuery("""
                    SELECT id, ordem, nome, dono_id, etapa_do_gestor FROM etapa
                    WHERE org_id = ? AND esteira_id = ? ORDER BY ordem
                    """).setParameter(1, org.valor()).setParameter(2, esteiraId).getResultList();
            List<EsteiraDados.EtapaDados> etapas = new ArrayList<>(ets.size());
            for (Object[] t : ets) {
                etapas.add(new EsteiraDados.EtapaDados(t[0].toString(), ((Number) t[1]).intValue(),
                        (String) t[2], t[3] == null ? null : t[3].toString(), (Boolean) t[4]));
            }
            res.add(new EsteiraDados(esteiraId.toString(), (String) e[1], etapas));
        }
        return res;
    }

    @Override
    public UUID criarInstancia(OrgId org, UUID esteiraId, String entidadeExterna) {
        UUID primeira = etapaPorOrdem(org, esteiraId, true);
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO instancia (id, org_id, esteira_id, entidade_externa, etapa_atual_id)
                VALUES (?, ?, ?, ?, ?)
                """).setParameter(1, id).setParameter(2, org.valor()).setParameter(3, esteiraId)
                .setParameter(4, entidadeExterna).setParameter(5, primeira).executeUpdate();
        return id;
    }

    @Override
    public List<InstanciaDados> instancias(OrgId org, UUID esteiraId, UUID etapaFiltro) {
        StringBuilder sql = new StringBuilder("""
                SELECT i.id, i.entidade_externa, i.etapa_atual_id, et.nome, i.status, i.entrou_em
                FROM instancia i LEFT JOIN etapa et ON et.id = i.etapa_atual_id AND et.org_id = i.org_id
                WHERE i.org_id = ? AND i.esteira_id = ?
                """);
        if (etapaFiltro != null) {
            sql.append(" AND i.etapa_atual_id = ?");
        }
        sql.append(" ORDER BY i.entrou_em DESC");
        var q = em.createNativeQuery(sql.toString()).setParameter(1, org.valor()).setParameter(2, esteiraId);
        if (etapaFiltro != null) {
            q.setParameter(3, etapaFiltro);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = q.getResultList();
        List<InstanciaDados> res = new ArrayList<>(linhas.size());
        for (Object[] l : linhas) {
            res.add(new InstanciaDados(l[0].toString(), (String) l[1],
                    l[2] == null ? null : l[2].toString(), (String) l[3], (String) l[4], toOdt(l[5])));
        }
        return res;
    }

    @Override
    public void avancar(OrgId org, UUID instanciaId) {
        Object[] i;
        try {
            i = (Object[]) em.createNativeQuery(
                    "SELECT esteira_id, etapa_atual_id FROM instancia WHERE org_id = ? AND id = ?")
                    .setParameter(1, org.valor()).setParameter(2, instanciaId).getSingleResult();
        } catch (NoResultException e) {
            throw new java.util.NoSuchElementException("instância não encontrada");
        }
        UUID esteiraId = (UUID) i[0];
        UUID atual = (UUID) i[1];
        UUID proxima = proximaEtapa(org, esteiraId, atual);
        if (proxima == null) {
            em.createNativeQuery(
                    "UPDATE instancia SET status = 'CONCLUIDA', etapa_atual_id = NULL WHERE org_id = ? AND id = ?")
                    .setParameter(1, org.valor()).setParameter(2, instanciaId).executeUpdate();
        } else {
            em.createNativeQuery("UPDATE instancia SET etapa_atual_id = ? WHERE org_id = ? AND id = ?")
                    .setParameter(1, proxima).setParameter(2, org.valor()).setParameter(3, instanciaId).executeUpdate();
        }
    }

    // ---- checklist versionado ------------------------------------------------

    @Override
    public ChecklistDados checklistVigente(OrgId org, UUID esteiraId) {
        UUID etapaGestor = etapaDoGestor(org, esteiraId);
        if (etapaGestor == null) {
            return null;
        }
        Object[] c;
        try {
            c = (Object[]) em.createNativeQuery("""
                    SELECT id, versao FROM checklist WHERE org_id = ? AND etapa_id = ?
                    ORDER BY versao DESC LIMIT 1
                    """).setParameter(1, org.valor()).setParameter(2, etapaGestor).getSingleResult();
        } catch (NoResultException e) {
            return new ChecklistDados(etapaGestor.toString(), 0, List.of());
        }
        UUID checklistId = (UUID) c[0];
        return new ChecklistDados(etapaGestor.toString(), ((Number) c[1]).intValue(), criterios(org, checklistId));
    }

    @Override
    public int publicarChecklist(OrgId org, UUID esteiraId, List<NovoCriterio> criterios) {
        UUID etapaGestor = etapaDoGestor(org, esteiraId);
        if (etapaGestor == null) {
            throw new IllegalStateException("esteira sem etapa do gestor");
        }
        int versao = ((Number) em.createNativeQuery(
                "SELECT coalesce(max(versao), 0) + 1 FROM checklist WHERE org_id = ? AND etapa_id = ?")
                .setParameter(1, org.valor()).setParameter(2, etapaGestor).getSingleResult()).intValue();
        UUID checklistId = criarChecklist(org, etapaGestor, versao);
        for (NovoCriterio c : criterios) {
            em.createNativeQuery("""
                    INSERT INTO criterio (id, org_id, checklist_id, chave, descricao, tipo, obrigatorio)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """).setParameter(1, UUID.randomUUID()).setParameter(2, org.valor())
                    .setParameter(3, checklistId).setParameter(4, c.chave()).setParameter(5, c.descricao())
                    .setParameter(6, c.tipo()).setParameter(7, c.obrigatorio()).executeUpdate();
        }
        return versao;
    }

    // ---- avaliação (regra de avanço) -----------------------------------------

    @Override
    public AvaliacaoResultado avaliar(OrgId org, UUID instanciaId,
                                      List<ResultadoItem> resultados, List<ApontamentoItem> apontamentos,
                                      UUID avaliadorId) {
        Object[] inst;
        try {
            inst = (Object[]) em.createNativeQuery("""
                    SELECT i.esteira_id, i.etapa_atual_id, i.entidade_externa, e.nome, et.nome
                    FROM instancia i JOIN esteira e ON e.id = i.esteira_id AND e.org_id = i.org_id
                    LEFT JOIN etapa et ON et.id = i.etapa_atual_id AND et.org_id = i.org_id
                    WHERE i.org_id = ? AND i.id = ?
                    """).setParameter(1, org.valor()).setParameter(2, instanciaId).getSingleResult();
        } catch (NoResultException e) {
            throw new java.util.NoSuchElementException("instância não encontrada");
        }
        UUID etapaId = (UUID) inst[1];
        String entidade = (String) inst[2];
        String esteiraNome = (String) inst[3];
        String etapaNome = (String) inst[4];
        if (etapaId == null) {
            throw new IllegalStateException("instância já concluída");
        }

        ChecklistDados chk = checklistDaEtapa(org, etapaId);
        Map<String, String> res = new LinkedHashMap<>();
        for (ResultadoItem r : resultados) {
            res.put(r.criterioChave(), r.resultado());
        }
        boolean falhaObjetiva = chk.criterios().stream()
                .anyMatch(c -> c.tipo().equals("OBJETIVO") && c.obrigatorio() && "FALHOU".equals(res.get(c.chave())));
        boolean julgamentoPendente = chk.criterios().stream().anyMatch(c -> c.tipo().equals("JULGAMENTO"))
                || apontamentos.stream().anyMatch(a -> "JULGAMENTO".equals(a.tipo()));
        String desfecho = falhaObjetiva ? "REPROVADA" : julgamentoPendente ? "PENDENTE_JULGAMENTO" : "APROVADA";

        UUID avaliacaoId = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO avaliacao (id, org_id, instancia_id, etapa_id, checklist_versao, desfecho, avaliador_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """).setParameter(1, avaliacaoId).setParameter(2, org.valor()).setParameter(3, instanciaId)
                .setParameter(4, etapaId).setParameter(5, chk.versao()).setParameter(6, desfecho)
                .setParameter(7, avaliadorId).executeUpdate();
        for (ApontamentoItem a : apontamentos) {
            em.createNativeQuery("""
                    INSERT INTO apontamento (id, org_id, avaliacao_id, texto, tipo) VALUES (?, ?, ?, ?, ?)
                    """).setParameter(1, UUID.randomUUID()).setParameter(2, org.valor())
                    .setParameter(3, avaliacaoId).setParameter(4, a.texto()).setParameter(5, a.tipo()).executeUpdate();
        }

        if ("APROVADA".equals(desfecho)) {
            avancar(org, instanciaId);
            return new AvaliacaoResultado(desfecho, null);
        }
        // gera pendência ESTEIRA com o resultado anexado (RFC-0006)
        UUID pendenciaId = UUID.randomUUID();
        String trava = desfecho.equals("REPROVADA") ? "reprovada na etapa " + etapaNome
                : "aguarda julgamento na etapa " + etapaNome;
        em.createNativeQuery("""
                INSERT INTO pendencia (id, org_id, titulo, o_que_trava, classe, horizonte, status)
                VALUES (?, ?, ?, ?, 'ESTEIRA', 'SEMANA', 'ENTRADA')
                """).setParameter(1, pendenciaId).setParameter(2, org.valor())
                .setParameter(3, esteiraNome + ": " + entidade).setParameter(4, trava).executeUpdate();
        trilha.registrar(new EventoTrilha(org, pendenciaId, TipoEvento.CAPTADA,
                Ator.sistemaMotor("esteira"), null, "ENTRADA", null,
                "{\"instancia_id\":\"" + instanciaId + "\",\"avaliacao_id\":\"" + avaliacaoId + "\",\"desfecho\":\"" + desfecho + "\"}"));
        return new AvaliacaoResultado(desfecho, pendenciaId.toString());
    }

    // ---- mineração §B --------------------------------------------------------

    @Override
    public PropostaChecklist propostas(OrgId org, UUID esteiraId) {
        UUID etapaGestor = etapaDoGestor(org, esteiraId);
        if (etapaGestor == null) {
            return new PropostaChecklist(List.of(), List.of());
        }
        long reprovacoes = ((Number) em.createNativeQuery("""
                SELECT count(*) FROM avaliacao WHERE org_id = ? AND etapa_id = ? AND desfecho = 'REPROVADA'
                  AND avaliada_em >= now() - interval '90 days'
                """).setParameter(1, org.valor()).setParameter(2, etapaGestor).getSingleResult()).longValue();
        if (reprovacoes < minReprovacoes) {
            return new PropostaChecklist(List.of(), List.of());
        }
        // apontamentos OBJETIVO: em quantas reprovações distintas cada texto aparece
        @SuppressWarnings("unchecked")
        List<Object[]> obj = em.createNativeQuery("""
                SELECT ap.texto, count(DISTINCT a.id) FROM apontamento ap
                JOIN avaliacao a ON a.id = ap.avaliacao_id AND a.org_id = ap.org_id
                WHERE ap.org_id = ? AND a.etapa_id = ? AND a.desfecho = 'REPROVADA'
                  AND a.avaliada_em >= now() - interval '90 days' AND ap.tipo = 'OBJETIVO'
                GROUP BY ap.texto
                """).setParameter(1, org.valor()).setParameter(2, etapaGestor).getResultList();
        var vigentes = chavesVigentes(org, etapaGestor);
        List<PropostaChecklist.CandidatoCriterio> objetivos = new ArrayList<>();
        for (Object[] o : obj) {
            String texto = (String) o[0];
            double fracao = ((Number) o[1]).longValue() / (double) reprovacoes;
            String chave = slug(texto);
            if (fracao >= 0.5 && !vigentes.contains(chave)) {
                objetivos.add(new PropostaChecklist.CandidatoCriterio(chave, texto, fracao));
            }
        }
        @SuppressWarnings("unchecked")
        List<String> julg = em.createNativeQuery("""
                SELECT DISTINCT ap.texto FROM apontamento ap
                JOIN avaliacao a ON a.id = ap.avaliacao_id AND a.org_id = ap.org_id
                WHERE ap.org_id = ? AND a.etapa_id = ? AND a.desfecho = 'REPROVADA'
                  AND a.avaliada_em >= now() - interval '90 days' AND ap.tipo = 'JULGAMENTO'
                """).setParameter(1, org.valor()).setParameter(2, etapaGestor).getResultList();
        return new PropostaChecklist(objetivos, julg);
    }

    // ---- auxiliares ----------------------------------------------------------

    private UUID criarChecklist(OrgId org, UUID etapaId, int versao) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery("INSERT INTO checklist (id, org_id, etapa_id, versao) VALUES (?, ?, ?, ?)")
                .setParameter(1, id).setParameter(2, org.valor()).setParameter(3, etapaId)
                .setParameter(4, versao).executeUpdate();
        return id;
    }

    private ChecklistDados checklistDaEtapa(OrgId org, UUID etapaId) {
        Object[] c;
        try {
            c = (Object[]) em.createNativeQuery(
                    "SELECT id, versao FROM checklist WHERE org_id = ? AND etapa_id = ? ORDER BY versao DESC LIMIT 1")
                    .setParameter(1, org.valor()).setParameter(2, etapaId).getSingleResult();
        } catch (NoResultException e) {
            return new ChecklistDados(etapaId.toString(), 0, List.of());
        }
        return new ChecklistDados(etapaId.toString(), ((Number) c[1]).intValue(), criterios(org, (UUID) c[0]));
    }

    private List<ChecklistDados.CriterioDados> criterios(OrgId org, UUID checklistId) {
        @SuppressWarnings("unchecked")
        List<Object[]> cs = em.createNativeQuery("""
                SELECT chave, descricao, tipo, obrigatorio FROM criterio WHERE org_id = ? AND checklist_id = ?
                """).setParameter(1, org.valor()).setParameter(2, checklistId).getResultList();
        List<ChecklistDados.CriterioDados> res = new ArrayList<>(cs.size());
        for (Object[] c : cs) {
            res.add(new ChecklistDados.CriterioDados((String) c[0], (String) c[1], (String) c[2], (Boolean) c[3]));
        }
        return res;
    }

    private java.util.Set<String> chavesVigentes(OrgId org, UUID etapaGestor) {
        ChecklistDados chk = checklistDaEtapa(org, etapaGestor);
        java.util.Set<String> s = new java.util.HashSet<>();
        chk.criterios().forEach(c -> s.add(c.chave()));
        return s;
    }

    private UUID etapaDoGestor(OrgId org, UUID esteiraId) {
        try {
            return (UUID) em.createNativeQuery("""
                    SELECT id FROM etapa WHERE org_id = ? AND esteira_id = ? AND etapa_do_gestor
                    ORDER BY ordem LIMIT 1
                    """).setParameter(1, org.valor()).setParameter(2, esteiraId).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private UUID etapaPorOrdem(OrgId org, UUID esteiraId, boolean primeira) {
        try {
            return (UUID) em.createNativeQuery(
                    "SELECT id FROM etapa WHERE org_id = ? AND esteira_id = ? ORDER BY ordem "
                            + (primeira ? "ASC" : "DESC") + " LIMIT 1")
                    .setParameter(1, org.valor()).setParameter(2, esteiraId).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private UUID proximaEtapa(OrgId org, UUID esteiraId, UUID atual) {
        try {
            return (UUID) em.createNativeQuery("""
                    SELECT id FROM etapa WHERE org_id = ? AND esteira_id = ? AND ordem > (
                        SELECT ordem FROM etapa WHERE org_id = ? AND id = ?)
                    ORDER BY ordem ASC LIMIT 1
                    """).setParameter(1, org.valor()).setParameter(2, esteiraId)
                    .setParameter(3, org.valor()).setParameter(4, atual).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private static String slug(String texto) {
        String s = texto.toLowerCase().trim().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        return s.isEmpty() ? "criterio" : s;
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
