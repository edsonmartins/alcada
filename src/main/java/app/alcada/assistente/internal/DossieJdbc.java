package app.alcada.assistente.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import app.alcada.assistente.port.Dossie;
import app.alcada.assistente.port.RespostaDossie;
import app.alcada.plataforma.gateway.port.ModelGateway;
import app.alcada.plataforma.gateway.port.Sensibilidade;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaEmbedding;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaRedacao;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/**
 * Índice híbrido do dossiê (RFC-0004 §1): BM25 (tsvector) + embeddings (pgvector).
 * Toda resposta cita fonte; sem passagem acima do limiar, "não encontrei na base"
 * (não completa de memória). Escopado por org_id (INV-15). Sem modelo, indexa só
 * o `tsv` e recupera por BM25 — o caminho vetorial ativa com embedding real.
 */
@ApplicationScoped
public class DossieJdbc implements Dossie {

    private static final int DIM = 1024;
    private static final int TOP_K = 5;

    private final EntityManager em;
    private final ModelGateway modelo;

    public DossieJdbc(EntityManager em, ModelGateway modelo) {
        this.em = em;
        this.modelo = modelo;
    }

    @Override
    public void indexar(OrgId org, UUID pendenciaId) {
        var orgId = org.valor();
        em.createNativeQuery("DELETE FROM documento_indice WHERE org_id = ? AND pendencia_id = ?")
                .setParameter(1, orgId).setParameter(2, pendenciaId).executeUpdate();

        Object[] p;
        try {
            p = (Object[]) em.createNativeQuery(
                    "SELECT titulo, o_que_trava, quem_espera FROM pendencia WHERE org_id = ? AND id = ?")
                    .setParameter(1, orgId).setParameter(2, pendenciaId).getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            return;
        }
        StringBuilder pend = new StringBuilder((String) p[0]);
        if (p[1] != null) {
            pend.append(". ").append(p[1]);
        }
        if (p[2] != null) {
            pend.append(". Quem espera: ").append(p[2]);
        }
        inserir(orgId, pendenciaId, "PENDENCIA", pendenciaId.toString(), pend.toString());

        // Mensagens brutas ligadas ao item (via cobrança) — best-effort.
        @SuppressWarnings("unchecked")
        List<Object[]> msgs = em.createNativeQuery("""
                SELECT eb.id, eb.texto FROM evento_bruto eb
                JOIN cobranca c ON c.evento_bruto_id = eb.id AND c.org_id = eb.org_id
                WHERE c.org_id = ? AND c.pendencia_id = ?
                """).setParameter(1, orgId).setParameter(2, pendenciaId).getResultList();
        for (Object[] m : msgs) {
            if (m[1] != null) {
                inserir(orgId, pendenciaId, "MENSAGEM", m[0].toString(), (String) m[1]);
            }
        }
    }

    @Override
    public RespostaDossie perguntar(OrgId org, UUID pendenciaId, String pergunta) {
        indexar(org, pendenciaId); // frescor (barato para um item)
        // A busca ignora os termos de data/mês: eles são a premissa a verificar, não
        // o tópico. Com plainto_tsquery (que faz AND) eles impediriam a recuperação da
        // passagem que justamente contradiz a premissa.
        String busca = semTermosDeData(pergunta);
        String qemb = embedding(org, pendenciaId, busca);

        @SuppressWarnings("unchecked")
        List<Object[]> linhas = (qemb == null ? bm25(org, busca) : hibrido(org, busca, qemb));

        if (linhas.isEmpty()) {
            return new RespostaDossie(false, "Não encontrei isso na base.", List.of(), null);
        }
        List<RespostaDossie.Fonte> fontes = new ArrayList<>(linhas.size());
        StringBuilder ctx = new StringBuilder();
        for (Object[] l : linhas) {
            fontes.add(new RespostaDossie.Fonte((String) l[0], l[1] == null ? null : l[1].toString(), (String) l[2]));
            ctx.append("- ").append((String) l[2]).append("\n");
        }
        // Correção de premissa (RFC-0004 §1): a base contradiz um fato da pergunta.
        String correcao = corrigirPremissa(pergunta, linhas);
        String resposta;
        try {
            String instrucao = "Pergunta: " + pergunta + "\nPassagens:\n" + ctx
                    + "\nSe a pergunta afirmar um fato que as passagens contradizem, corrija a premissa "
                    + "explicitamente, citando o dado correto da base."
                    + (correcao != null ? "\nCorreção factual verificada: " + correcao : "");
            resposta = modelo.redigir(new TarefaRedacao(org, Sensibilidade.INTERNA, pendenciaId,
                    instrucao, "direto")).rascunho();
        } catch (RuntimeException e) {
            // sem modelo: as próprias passagens (com fonte); a correção sai no campo próprio.
            resposta = (correcao != null ? correcao + "\n\n" : "") + ctx.toString().trim();
        }
        return new RespostaDossie(true, resposta, fontes, correcao);
    }

    /**
     * Correção de premissa determinística para datas/meses (o caso do RFC): se a
     * pergunta afirma um mês (por nome ou em dd/mm) e a base traz uma data de mês
     * diferente, devolve a correção citando a data da base. Cobre o piloto sem LLM.
     */
    static String corrigirPremissa(String pergunta, List<Object[]> passagens) {
        java.util.Set<Integer> mesesPergunta = mesesMencionados(pergunta);
        if (mesesPergunta.isEmpty()) {
            return null;
        }
        java.util.regex.Matcher m = DATA.matcher("");
        for (Object[] l : passagens) {
            String texto = l[2] == null ? "" : l[2].toString();
            m.reset(texto);
            while (m.find()) {
                int mes = Integer.parseInt(m.group(2));
                if (mes >= 1 && mes <= 12 && !mesesPergunta.contains(mes)) {
                    int mp = mesesPergunta.iterator().next();
                    return "A pergunta menciona " + MES_NOME[mp - 1] + ", mas a base indica " + m.group(0) + ".";
                }
            }
        }
        return null;
    }

    /** Remove datas dd/mm e nomes de mês do texto de busca (a premissa a verificar). */
    private static String semTermosDeData(String pergunta) {
        if (pergunta == null) {
            return "";
        }
        String s = DATA.matcher(pergunta).replaceAll(" ");
        s = s.replaceAll("(?iu)\\b(janeiro|fevereiro|março|marco|abril|maio|junho|julho|agosto"
                + "|setembro|outubro|novembro|dezembro)\\b", " ");
        String limpo = s.replaceAll("\\s+", " ").trim();
        return limpo.isBlank() ? pergunta : limpo;
    }

    private static final String[] MES_NOME = {
            "janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"};

    private static final java.util.regex.Pattern DATA =
            java.util.regex.Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b");

    /** Meses afirmados na pergunta: por nome (sem acento) e por dd/mm. */
    private static java.util.Set<Integer> mesesMencionados(String pergunta) {
        java.util.Set<Integer> meses = new java.util.LinkedHashSet<>();
        String q = java.text.Normalizer.normalize(pergunta == null ? "" : pergunta,
                java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase();
        String[] nomes = {"janeiro", "fevereiro", "marco", "abril", "maio", "junho",
                "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"};
        for (int i = 0; i < nomes.length; i++) {
            if (q.contains(nomes[i])) {
                meses.add(i + 1);
            }
        }
        java.util.regex.Matcher m = DATA.matcher(q);
        while (m.find()) {
            int mes = Integer.parseInt(m.group(2));
            if (mes >= 1 && mes <= 12) {
                meses.add(mes);
            }
        }
        return meses;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> bm25(OrgId org, String pergunta) {
        return em.createNativeQuery("""
                SELECT fonte_tipo, fonte_ref, texto
                FROM documento_indice
                WHERE org_id = ? AND tsv @@ plainto_tsquery('portuguese', ?)
                ORDER BY ts_rank_cd(tsv, plainto_tsquery('portuguese', ?)) DESC
                LIMIT ?
                """).setParameter(1, org.valor()).setParameter(2, pergunta).setParameter(3, pergunta)
                .setParameter(4, TOP_K).getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> hibrido(OrgId org, String pergunta, String qemb) {
        // BM25 + cosseno (quando há embedding). Ordena pela soma dos escores.
        return em.createNativeQuery("""
                SELECT fonte_tipo, fonte_ref, texto,
                       ts_rank_cd(tsv, plainto_tsquery('portuguese', ?))
                         + CASE WHEN emb IS NULL THEN 0 ELSE 1 - (emb <=> CAST(? AS vector)) END AS score
                FROM documento_indice
                WHERE org_id = ? AND (tsv @@ plainto_tsquery('portuguese', ?) OR emb IS NOT NULL)
                ORDER BY score DESC
                LIMIT ?
                """).setParameter(1, pergunta).setParameter(2, qemb).setParameter(3, org.valor())
                .setParameter(4, pergunta).setParameter(5, TOP_K).getResultList();
    }

    private void inserir(UUID orgId, UUID pendenciaId, String tipo, String ref, String texto) {
        String emb = embeddingRaw(new OrgId(orgId), pendenciaId, texto);
        em.createNativeQuery("""
                INSERT INTO documento_indice (org_id, pendencia_id, fonte_tipo, fonte_ref, texto, emb)
                VALUES (?, ?, ?, ?, ?, CAST(NULLIF(?, '') AS vector))
                """).setParameter(1, orgId).setParameter(2, pendenciaId).setParameter(3, tipo)
                .setParameter(4, ref).setParameter(5, texto).setParameter(6, emb == null ? "" : emb)
                .executeUpdate();
    }

    /** Embedding do texto como literal pgvector, ou null (sem modelo / dimensão diferente). */
    private String embeddingRaw(OrgId org, UUID ref, String texto) {
        try {
            float[] v = modelo.embutir(new TarefaEmbedding(org, Sensibilidade.INTERNA, ref, texto)).vetor();
            return v != null && v.length == DIM ? vetorLiteral(v) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String embedding(OrgId org, UUID ref, String texto) {
        return embeddingRaw(org, ref, texto);
    }

    private static String vetorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
