package app.alcada.consulta.internal;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import app.alcada.consulta.port.Consulta;
import app.alcada.consulta.port.ResultadoConsulta;
import app.alcada.consulta.port.ResultadoConsulta.Item;
import app.alcada.plataforma.gateway.port.ModelGateway;
import app.alcada.plataforma.gateway.port.Sensibilidade;
import app.alcada.plataforma.gateway.port.Tarefas.Extracao;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaExtracao;
import app.alcada.plataforma.multitenancy.port.OrgId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Consulta em linguagem natural sobre a fila (RFC-0004 §3). O planejamento
 * escolhe um {@link Template} de uma whitelist fechada: quando {@code
 * consulta.usar-llm=true}, o gateway (schema estrito) monta a consulta; caso
 * contrário — e sempre que o modelo falhar — cai num classificador
 * determinístico por palavras-chave (o piloto roda sem LLM real). A execução é
 * SQL determinístico, escopado por {@code org_id} (INV-15); nada é gerado pelo
 * modelo além do par {template, filtro}.
 */
@ApplicationScoped
public class ConsultaJdbc implements Consulta {

    private static final int LIMITE_ITENS = 25;

    /** Templates parametrizados permitidos. Nenhuma consulta fora desta lista roda. */
    enum Template {
        ESPERANDO_MIM, TRAVADO_POR, AVERSIVOS, DELEGADAS_ABERTAS, POR_CLASSE, VALOR_TOTAL, DESCONHECIDO;

        static Template seguro(String s) {
            if (s == null) {
                return null;
            }
            try {
                return valueOf(s.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    record Plano(Template template, String filtro) {
    }

    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,
             "properties":{
               "template":{"type":"string","enum":["ESPERANDO_MIM","TRAVADO_POR","AVERSIVOS",
                 "DELEGADAS_ABERTAS","POR_CLASSE","VALOR_TOTAL","DESCONHECIDO"]},
               "filtro":{"type":"string"}},
             "required":["template"]}""";

    private final EntityManager em;
    private final ModelGateway modelo;
    private final ObjectMapper json;

    @ConfigProperty(name = "consulta.usar-llm", defaultValue = "false")
    boolean usarLlm;

    public ConsultaJdbc(EntityManager em, ModelGateway modelo, ObjectMapper json) {
        this.em = em;
        this.modelo = modelo;
        this.json = json;
    }

    @Override
    @Transactional
    public ResultadoConsulta consultar(OrgId org, String pergunta) {
        String p = pergunta == null ? "" : pergunta.trim();
        Plano plano = planejar(org, p);
        return switch (plano.template()) {
            case ESPERANDO_MIM -> esperandoMim(org, p);
            case TRAVADO_POR -> travadoPor(org, p, plano.filtro());
            case AVERSIVOS -> aversivos(org, p);
            case DELEGADAS_ABERTAS -> delegadasAbertas(org, p);
            case POR_CLASSE -> porClasse(org, p, plano.filtro());
            case VALOR_TOTAL -> valorTotal(org, p);
            case DESCONHECIDO -> new ResultadoConsulta(p, "DESCONHECIDO",
                    "Não sei responder isso sobre a fila. Tente algo como “quanto está esperando por mim”, "
                            + "“o que trava por causa do financeiro” ou “o que estou adiando”.",
                    List.of());
        };
    }

    // ---- planejamento -------------------------------------------------------

    private Plano planejar(OrgId org, String pergunta) {
        if (usarLlm) {
            try {
                Extracao<Plano> ex = modelo.extrair(new TarefaExtracao<>(
                        org, Sensibilidade.INTERNA, null, pergunta, SCHEMA, this::parsePlano));
                if (ex != null && ex.valor() != null && ex.valor().template() != null) {
                    return ex.valor();
                }
            } catch (RuntimeException degrada) {
                // modelo indisponível/recusado: cai no classificador determinístico.
            }
        }
        return porPalavrasChave(pergunta);
    }

    private Plano parsePlano(String conteudo) {
        try {
            JsonNode n = json.readTree(conteudo);
            Template t = Template.seguro(n.path("template").asText(null));
            String f = n.hasNonNull("filtro") ? n.get("filtro").asText() : null;
            return new Plano(t, vazioNulo(f));
        } catch (Exception e) {
            return new Plano(null, null);
        }
    }

    /** Classificador determinístico: cobre os exemplos do RFC sem LLM. */
    Plano porPalavrasChave(String pergunta) {
        String q = norm(pergunta);
        if (q.contains("esperando por mim") || q.contains("espera por mim")
                || (q.contains("esperando") && q.contains("mim"))
                || q.contains("parado esperando")) {
            return new Plano(Template.ESPERANDO_MIM, null);
        }
        if (q.contains("trava") || q.contains("travad") || q.contains("bloqueio") || q.contains("bloquead")) {
            return new Plano(Template.TRAVADO_POR, extrairArea(q));
        }
        if (q.contains("adiad") || q.contains("empurrando") || q.contains("barriga")
                || q.contains("aversiv") || q.contains("enrolando")) {
            return new Plano(Template.AVERSIVOS, null);
        }
        if (q.contains("deleg")) {
            return new Plano(Template.DELEGADAS_ABERTAS, null);
        }
        if (q.contains("quant")) {
            if (q.contains("decis")) {
                return new Plano(Template.POR_CLASSE, "DECISAO");
            }
            if (q.contains("bloqueio") || q.contains("bloquead")) {
                return new Plano(Template.POR_CLASSE, "BLOQUEIO");
            }
            if (q.contains("esteira")) {
                return new Plano(Template.POR_CLASSE, "ESTEIRA");
            }
        }
        if (q.contains("em jogo") || q.contains("dinheiro") || q.contains("valor")
                || (q.contains("quanto") && q.contains("fila"))) {
            return new Plano(Template.VALOR_TOTAL, null);
        }
        return new Plano(Template.DESCONHECIDO, null);
    }

    /** Extrai a "área" mencionada após "causa d…"/"conta d…" (ex.: financeiro). */
    private static String extrairArea(String q) {
        for (String marca : new String[] {"causa d", "conta d", "por causa de", "por conta de"}) {
            int i = q.indexOf(marca);
            if (i >= 0) {
                String resto = q.substring(i + marca.length()).trim();
                // remove artigos iniciais "o/a/os/as/e "
                resto = resto.replaceFirst("^(os |as |o |a |e )+", "").trim();
                if (!resto.isBlank()) {
                    return primeiraPalavra(resto);
                }
            }
        }
        return null;
    }

    private static String primeiraPalavra(String s) {
        int fim = 0;
        while (fim < s.length() && (Character.isLetterOrDigit(s.charAt(fim)))) {
            fim++;
        }
        String w = fim > 0 ? s.substring(0, fim) : s;
        return w.isBlank() ? null : w;
    }

    // ---- templates (SQL determinístico, org-escopado) -----------------------

    private ResultadoConsulta esperandoMim(OrgId org, String pergunta) {
        Object[] agg = (Object[]) em.createNativeQuery("""
                SELECT count(*), coalesce(sum(valor_em_jogo), 0)
                FROM pendencia WHERE org_id = ? AND status = 'ENTRADA'
                """).setParameter(1, org.valor()).getSingleResult();
        long n = ((Number) agg[0]).longValue();
        double soma = ((Number) agg[1]).doubleValue();
        List<Item> itens = itens("""
                SELECT id, titulo, classe, valor_em_jogo FROM pendencia
                WHERE org_id = ? AND status = 'ENTRADA'
                ORDER BY valor_em_jogo DESC NULLS LAST, criada_em ASC LIMIT %d
                """.formatted(LIMITE_ITENS), org);
        String resp = n == 0 ? "Nada esperando por você agora."
                : "Há " + n + plural(n, " item", " itens") + " esperando por você"
                        + (soma > 0 ? ", somando " + reais(soma) + "." : ".");
        return new ResultadoConsulta(pergunta, "ESPERANDO_MIM", resp, itens);
    }

    private ResultadoConsulta travadoPor(OrgId org, String pergunta, String area) {
        String sql = """
                SELECT id, titulo, classe, valor_em_jogo FROM pendencia
                WHERE org_id = ? AND status <> 'FECHADA' AND classe = 'BLOQUEIO'
                  AND (CAST(? AS text) IS NULL
                       OR quem_espera ILIKE ? OR o_que_trava ILIKE ? OR titulo ILIKE ?)
                ORDER BY valor_em_jogo DESC NULLS LAST, criada_em ASC LIMIT %d
                """.formatted(LIMITE_ITENS);
        String like = area == null ? null : "%" + area + "%";
        List<Item> itens = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(sql)
                .setParameter(1, org.valor()).setParameter(2, area)
                .setParameter(3, like).setParameter(4, like).setParameter(5, like)
                .getResultList();
        for (Object[] l : linhas) {
            itens.add(item(l));
        }
        long n = itens.size();
        String alvo = area == null ? "" : " ligados a “" + area + "”";
        String resp = n == 0 ? ("Nenhum bloqueio aberto" + alvo + ".")
                : (n + plural(n, " bloqueio aberto", " bloqueios abertos") + alvo + ".");
        return new ResultadoConsulta(pergunta, "TRAVADO_POR", resp, itens);
    }

    private ResultadoConsulta aversivos(OrgId org, String pergunta) {
        List<Item> itens = itens("""
                SELECT id, titulo, classe, valor_em_jogo FROM pendencia
                WHERE org_id = ? AND status <> 'FECHADA' AND adiado_count >= 3
                ORDER BY adiado_count DESC, criada_em ASC LIMIT %d
                """.formatted(LIMITE_ITENS), org);
        long n = itens.size();
        String resp = n == 0 ? "Nada que você venha adiando 3 vezes ou mais."
                : (n + plural(n, " item adiado", " itens adiados") + " 3 vezes ou mais — vale decidir ou deixar morrer.");
        return new ResultadoConsulta(pergunta, "AVERSIVOS", resp, itens);
    }

    private ResultadoConsulta delegadasAbertas(OrgId org, String pergunta) {
        List<Item> itens = itens("""
                SELECT id, titulo, classe, valor_em_jogo FROM pendencia
                WHERE org_id = ? AND status = 'DELEGADA'
                ORDER BY valor_em_jogo DESC NULLS LAST, criada_em ASC LIMIT %d
                """.formatted(LIMITE_ITENS), org);
        long n = itens.size();
        String resp = n == 0 ? "Nada delegado em aberto."
                : (n + plural(n, " item delegado", " itens delegados") + " ainda em aberto.");
        return new ResultadoConsulta(pergunta, "DELEGADAS_ABERTAS", resp, itens);
    }

    private ResultadoConsulta porClasse(OrgId org, String pergunta, String classe) {
        String c = classe == null ? "DECISAO" : classe;
        List<Item> itens = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT id, titulo, classe, valor_em_jogo FROM pendencia
                WHERE org_id = ? AND status <> 'FECHADA' AND classe = ?
                ORDER BY valor_em_jogo DESC NULLS LAST, criada_em ASC LIMIT %d
                """.formatted(LIMITE_ITENS), Object[].class)
                .setParameter(1, org.valor()).setParameter(2, c).getResultList();
        for (Object[] l : linhas) {
            itens.add(item(l));
        }
        long n = itens.size();
        String rot = switch (c) {
            case "BLOQUEIO" -> plural(n, "bloqueio aberto", "bloqueios abertos");
            case "ESTEIRA" -> plural(n, "item de esteira aberto", "itens de esteira abertos");
            default -> plural(n, "decisão aberta", "decisões abertas");
        };
        return new ResultadoConsulta(pergunta, "POR_CLASSE", "Você tem " + n + " " + rot + ".", itens);
    }

    private ResultadoConsulta valorTotal(OrgId org, String pergunta) {
        Object[] agg = (Object[]) em.createNativeQuery("""
                SELECT count(*), coalesce(sum(valor_em_jogo), 0)
                FROM pendencia WHERE org_id = ? AND status <> 'FECHADA' AND valor_em_jogo IS NOT NULL
                """).setParameter(1, org.valor()).getSingleResult();
        long n = ((Number) agg[0]).longValue();
        double soma = ((Number) agg[1]).doubleValue();
        List<Item> itens = itens("""
                SELECT id, titulo, classe, valor_em_jogo FROM pendencia
                WHERE org_id = ? AND status <> 'FECHADA' AND valor_em_jogo IS NOT NULL
                ORDER BY valor_em_jogo DESC LIMIT %d
                """.formatted(LIMITE_ITENS), org);
        String resp = n == 0 ? "Nenhum item aberto com valor em jogo registrado."
                : (reais(soma) + " em jogo na fila, em " + n + plural(n, " item", " itens") + ".");
        return new ResultadoConsulta(pergunta, "VALOR_TOTAL", resp, itens);
    }

    // ---- helpers ------------------------------------------------------------

    private List<Item> itens(String sql, OrgId org) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(sql).setParameter(1, org.valor()).getResultList();
        List<Item> itens = new ArrayList<>(linhas.size());
        for (Object[] l : linhas) {
            itens.add(item(l));
        }
        return itens;
    }

    private static Item item(Object[] l) {
        Double valor = l[3] == null ? null : ((Number) l[3]).doubleValue();
        return new Item(l[0].toString(), (String) l[1], (String) l[2], valor);
    }

    private static String plural(long n, String um, String muitos) {
        return n == 1 ? um : muitos;
    }

    private static String reais(double v) {
        if (v >= 1_000_000) {
            return "R$ " + arredonda(v / 1_000_000) + " mi";
        }
        if (v >= 1_000) {
            return "R$ " + arredonda(v / 1_000) + " mil";
        }
        return "R$ " + Math.round(v);
    }

    private static String arredonda(double v) {
        return (Math.round(v * 10) / 10.0 + "").replace(".0", "").replace('.', ',');
    }

    private static String vazioNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** minúsculas + sem acento, para casar palavras-chave de forma robusta. */
    private static String norm(String s) {
        String semAcento = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return semAcento.toLowerCase();
    }
}
