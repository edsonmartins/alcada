package app.alcada.consulta.internal;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import app.alcada.consulta.port.Consulta;
import app.alcada.consulta.port.ResultadoConsulta;
import app.alcada.consulta.port.ResultadoConsulta.Item;
import app.alcada.plataforma.gateway.port.ModelGateway;
import app.alcada.plataforma.gateway.port.Sensibilidade;
import app.alcada.plataforma.gateway.port.Tarefas.Extracao;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaExtracao;
import app.alcada.plataforma.multitenancy.port.FusoTenant;
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
        ESPERANDO_MIM, TRAVADO_POR, AVERSIVOS, DELEGADAS_ABERTAS, POR_CLASSE, VALOR_TOTAL,
        DECISOES_RECENTES, DESCONHECIDO;

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
                 "DELEGADAS_ABERTAS","POR_CLASSE","VALOR_TOTAL","DECISOES_RECENTES","DESCONHECIDO"]},
               "filtro":{"type":"string"}},
             "required":["template"]}""";

    private final EntityManager em;
    private final ModelGateway modelo;
    private final ObjectMapper json;
    private final FusoTenant fuso;

    @ConfigProperty(name = "consulta.usar-llm", defaultValue = "false")
    boolean usarLlm;

    public ConsultaJdbc(EntityManager em, ModelGateway modelo, ObjectMapper json, FusoTenant fuso) {
        this.em = em;
        this.modelo = modelo;
        this.json = json;
        this.fuso = fuso;
    }

    @Override
    @Transactional
    public ResultadoConsulta consultar(OrgId org, UUID gestor, String pergunta) {
        String p = pergunta == null ? "" : pergunta.trim();
        Plano plano = planejar(org, p);
        return switch (plano.template()) {
            case ESPERANDO_MIM -> esperandoMim(org, p, plano.filtro());
            case TRAVADO_POR -> travadoPor(org, p, plano.filtro());
            case AVERSIVOS -> aversivos(org, p);
            case DELEGADAS_ABERTAS -> delegadasAbertas(org, p);
            case POR_CLASSE -> porClasse(org, p, plano.filtro());
            case VALOR_TOTAL -> valorTotal(org, p);
            case DECISOES_RECENTES -> decisoesRecentes(org, gestor, p, plano.filtro());
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
                        org, Sensibilidade.INTERNA, null, promptClassificacao(pergunta), SCHEMA, this::parsePlano));
                if (ex != null && ex.valor() != null && ex.valor().template() != null) {
                    return ex.valor();
                }
            } catch (RuntimeException degrada) {
                // modelo indisponível/recusado: cai no classificador determinístico.
            }
        }
        return porPalavrasChave(pergunta);
    }

    /** Instrução que dá ao modelo a semântica de cada template (INV-10: ele só escolhe). */
    private static String promptClassificacao(String pergunta) {
        return """
                Classifique a pergunta do gestor sobre a FILA DE DECISÕES em UM template.
                Responda só com o JSON do schema (template e, quando fizer sentido, filtro).

                Templates:
                - ESPERANDO_MIM: o que aguarda a decisão do gestor / depende de mim / tenho pra fazer / \
                está parado esperando por mim. Se a pergunta mencionar um período, preencha "filtro" \
                com HOJE, SEMANA (esta semana / semana que vem) ou TRIMESTRE.
                - TRAVADO_POR: o que está bloqueado por causa de uma área ou pessoa. Preencha "filtro" \
                com essa área/pessoa (ex.: "financeiro", "TI").
                - AVERSIVOS: o que venho adiando / empurrando com a barriga (adiado várias vezes).
                - DELEGADAS_ABERTAS: o que deleguei a alguém e ainda está em aberto.
                - POR_CLASSE: contagem por tipo. Preencha "filtro" com DECISAO, BLOQUEIO ou ESTEIRA.
                - VALOR_TOTAL: quanto de valor/dinheiro está em jogo na fila.
                - DECISOES_RECENTES: o que o gestor JÁ decidiu/fez (resolveu, repassou, adiou) \
                num período. Preencha "filtro" com ONTEM, HOJE, SEMANA ou TRIMESTRE.
                - DESCONHECIDO: se a pergunta não for sobre a fila.

                Pergunta: """ + pergunta;
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
        // "o que eu decidi" vem antes de ESPERANDO_MIM: "decidi esta semana" não é a fila,
        // é a trilha de decisões.
        if (q.contains("decidi") || q.contains("o que fiz") || q.contains("que decisoes")
                || q.contains("decisoes que tomei") || q.contains("decisoes tomei")) {
            return new Plano(Template.DECISOES_RECENTES, horizonteDe(q));
        }
        if (q.contains("esperando por mim") || q.contains("espera por mim")
                || (q.contains("esperando") && q.contains("mim"))
                || q.contains("parado esperando") || q.contains("pra hoje") || q.contains("para hoje")
                || q.contains("semana que vem") || q.contains("proxima semana")
                || q.contains("essa semana") || q.contains("esta semana")
                || q.contains("o que tenho") || q.contains("o que eu tenho")
                || q.contains("depende de mim")) {
            return new Plano(Template.ESPERANDO_MIM, horizonteDe(q));
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

    /** Horizonte mencionado na fala (para o filtro de ESPERANDO_MIM). */
    private static String horizonteDe(String q) {
        if (q.contains("ontem")) {
            return "ONTEM";
        }
        if (q.contains("hoje")) {
            return "HOJE";
        }
        if (q.contains("semana")) {
            return "SEMANA";
        }
        if (q.contains("trimestre") || q.contains("mes que vem") || q.contains("mês que vem")) {
            return "TRIMESTRE";
        }
        return null;
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

    private ResultadoConsulta esperandoMim(OrgId org, String pergunta, String filtroHorizonte) {
        String h = horizonteValido(filtroHorizonte); // HOJE|SEMANA|TRIMESTRE ou null
        Object[] agg = (Object[]) em.createNativeQuery("""
                SELECT count(*), coalesce(sum(valor_em_jogo), 0)
                FROM pendencia WHERE org_id = ? AND status = 'ENTRADA'
                  AND (CAST(? AS text) IS NULL OR horizonte = ?)
                """).setParameter(1, org.valor()).setParameter(2, h).setParameter(3, h).getSingleResult();
        long n = ((Number) agg[0]).longValue();
        double soma = ((Number) agg[1]).doubleValue();
        List<Item> itens = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT id, titulo, classe, valor_em_jogo FROM pendencia
                WHERE org_id = ? AND status = 'ENTRADA' AND (CAST(? AS text) IS NULL OR horizonte = ?)
                ORDER BY valor_em_jogo DESC NULLS LAST, criada_em ASC LIMIT %d
                """.formatted(LIMITE_ITENS))
                .setParameter(1, org.valor()).setParameter(2, h).setParameter(3, h).getResultList();
        for (Object[] l : linhas) {
            itens.add(item(l));
        }
        String periodo = switch (h == null ? "" : h) {
            case "HOJE" -> " para hoje";
            case "SEMANA" -> " para esta semana";
            case "TRIMESTRE" -> " no trimestre";
            default -> "";
        };
        String resp = n == 0 ? ("Nada esperando por você" + periodo + ".")
                : "Há " + n + plural(n, " item", " itens") + " esperando por você" + periodo
                        + (soma > 0 ? ", somando " + reais(soma) + "." : ".");
        return new ResultadoConsulta(pergunta, "ESPERANDO_MIM", resp, itens);
    }

    private static String horizonteValido(String s) {
        if (s == null) {
            return null;
        }
        String u = s.trim().toUpperCase();
        return (u.equals("HOJE") || u.equals("SEMANA") || u.equals("TRIMESTRE")) ? u : null;
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

    /** O que o gestor já decidiu num período — lido da trilha (append-only), filtrado pelo ator. */
    private ResultadoConsulta decisoesRecentes(OrgId org, UUID gestor, String pergunta, String horizonte) {
        if (gestor == null) {
            return new ResultadoConsulta(pergunta, "DECISOES_RECENTES",
                    "Não consegui identificar quem está perguntando.", List.of());
        }
        ZoneId zona = fuso.fuso(org);
        String h = horizonte == null ? "SEMANA" : horizonte.trim().toUpperCase();
        LocalDate hoje = LocalDate.now(zona);
        OffsetDateTime inicioHoje = hoje.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime desde = switch (h) {
            case "ONTEM" -> hoje.minusDays(1).atStartOfDay(zona).toOffsetDateTime();
            case "HOJE" -> inicioHoje;
            case "TRIMESTRE" -> hoje.minusDays(90).atStartOfDay(zona).toOffsetDateTime();
            default -> hoje.minusDays(6).atStartOfDay(zona).toOffsetDateTime(); // SEMANA
        };
        OffsetDateTime ate = h.equals("ONTEM") ? inicioHoje : null;

        List<Item> itens = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT t.pendencia_id, p.titulo, p.classe, p.valor_em_jogo FROM trilha t
                JOIN pendencia p ON p.id = t.pendencia_id
                WHERE t.org_id = ? AND p.org_id = ? AND t.ator = ?
                  AND t.tipo IN ('RESOLVIDA','REPASSADA','ADIADA','RESERVADA','REPOUSADA',
                                 'DESCARTADA','DECIDIDA_NO_BLOCO')
                  AND t.ocorrido_em >= ?
                  AND (CAST(? AS timestamptz) IS NULL OR t.ocorrido_em < ?)
                ORDER BY t.ocorrido_em DESC LIMIT %d
                """.formatted(LIMITE_ITENS))
                .setParameter(1, org.valor()).setParameter(2, org.valor())
                .setParameter(3, "HUMANO:" + gestor)
                .setParameter(4, desde).setParameter(5, ate).setParameter(6, ate)
                .getResultList();
        for (Object[] l : linhas) {
            itens.add(item(l));
        }
        long n = itens.size();
        String periodo = switch (h) {
            case "ONTEM" -> " ontem";
            case "HOJE" -> " hoje";
            case "TRIMESTRE" -> " no trimestre";
            default -> " nos últimos 7 dias";
        };
        String resp = n == 0 ? ("Você não registrou decisões" + periodo + ".")
                : ("Você decidiu " + n + plural(n, " item", " itens") + periodo + ".");
        return new ResultadoConsulta(pergunta, "DECISOES_RECENTES", resp, itens);
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
