package app.alcada.movel.internal;

import java.util.List;
import java.util.UUID;

import app.alcada.consulta.port.Consulta;
import app.alcada.consulta.port.ResultadoConsulta;
import app.alcada.identidade.port.Pessoas;
import app.alcada.identidade.port.Pessoas.PessoaRef;
import app.alcada.plataforma.gateway.port.ModelGateway;
import app.alcada.plataforma.gateway.port.Sensibilidade;
import app.alcada.plataforma.gateway.port.Tarefas.Extracao;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaExtracao;
import app.alcada.plataforma.multitenancy.port.OrgId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Interpretação de fala livre por LLM (022, ADR-0014/0019): mapeia a fala do
 * gestor para UMA intenção de um conjunto fechado (INV-10 — o modelo só escolhe,
 * o código executa) e resolve o item da fila, usando o contexto da conversa para
 * follow-ups. No REPASSAR resolve o nome falado para um {@code pessoa_id} via o
 * diretório de pessoas ([[Pessoas]]), com memória de apelidos por gestor: quando
 * não reconhece o nome, oferece a lista para o gestor escolher e {@code aprende}
 * o termo dali em diante. Quando há mais de um candidato, devolve as opções para
 * o app perguntar (nunca decide sozinho). Nenhum efeito aqui: comandos voltam
 * para o app confirmar; consulta é leitura. Sem LLM, devolve NENHUMA (o app cai
 * no matcher offline).
 */
@ApplicationScoped
public class InterpretadorVoz {

    private static final String SCHEMA = """
            {"type":"object","additionalProperties":false,
             "properties":{
               "intencao":{"type":"string","enum":["RESOLVER","REPASSAR","ADIAR","REGISTRAR","CONSULTAR","NENHUMA"]},
               "item":{"type":"integer"},
               "donoNome":{"type":"string"},"nivel":{"type":"string"},
               "quandoVoltar":{"type":"string"},"tituloNovo":{"type":"string"},"pergunta":{"type":"string"}},
             "required":["intencao"]}""";

    private final ModelGateway modelo;
    private final Consulta consulta;
    private final Pessoas pessoas;
    private final ObjectMapper json = new ObjectMapper();

    public InterpretadorVoz(ModelGateway modelo, Consulta consulta, Pessoas pessoas) {
        this.modelo = modelo;
        this.consulta = consulta;
        this.pessoas = pessoas;
    }

    public record ItemFila(String id, String titulo) {
    }

    public record Candidato(String id, String nome) {
        static Candidato de(PessoaRef p) {
            return new Candidato(p.id().toString(), p.nome());
        }
    }

    /**
     * Resultado estruturado para o app: comando a confirmar, consulta respondida,
     * ou nada. {@code termoFalado} != null (com {@code candidatosDono}) sinaliza
     * que o nome não foi reconhecido: se o gestor escolher da lista, o app pede
     * para aprender o apelido (termo→pessoa).
     */
    public record Resultado(
            String intencao, String pendenciaId, String titulo,
            String donoId, String donoNome, String nivel, String tituloNovo,
            String resposta, String frase, boolean precisaConfirmar,
            List<Candidato> candidatosDono, String termoFalado) {
    }

    public Resultado interpretar(OrgId org, UUID gestorId, String texto, List<String> contexto,
            List<ItemFila> fila) {
        Bruto b;
        try {
            Extracao<Bruto> ex = modelo.extrair(new TarefaExtracao<>(
                    org, Sensibilidade.INTERNA, null, prompt(texto, contexto, fila), SCHEMA, this::parse));
            b = ex == null ? null : ex.valor();
        } catch (RuntimeException degrada) {
            b = null;
        }
        if (b == null || b.intencao == null) {
            return nada("Não entendi. Pode repetir?");
        }
        return switch (b.intencao) {
            case "CONSULTAR" -> {
                ResultadoConsulta rc = consulta.consultar(org, vazioOu(b.pergunta, texto));
                yield new Resultado("CONSULTAR", null, null, null, null, null, null,
                        rc.resposta(), rc.resposta(), false, List.of(), null);
            }
            case "REGISTRAR" -> {
                String t = vazioOu(b.tituloNovo, texto);
                yield new Resultado("REGISTRAR", null, null, null, null, null, t, null,
                        "Registrar “" + t + "”. Confirma?", true, List.of(), null);
            }
            case "RESOLVER", "ADIAR" -> {
                ItemFila alvo = alvo(fila, b.item);
                if (alvo == null) {
                    yield nada("Não identifiquei o item na fila. Qual deles?");
                }
                String verbo = b.intencao.equals("RESOLVER") ? "Resolver" : "Adiar";
                yield new Resultado(b.intencao, alvo.id(), alvo.titulo(), null, null, null, null, null,
                        verbo + " “" + alvo.titulo() + "”. Confirma?", true, List.of(), null);
            }
            case "REPASSAR" -> repassar(org, gestorId, b, fila);
            default -> nada("Não entendi. Pode repetir?");
        };
    }

    /** Repasse: precisa de item + dono resolvido a partir do diretório de pessoas. */
    private Resultado repassar(OrgId org, UUID gestorId, Bruto b, List<ItemFila> fila) {
        ItemFila alvo = alvo(fila, b.item);
        if (alvo == null) {
            return nada("Não identifiquei o item para repassar.");
        }
        String nivel = b.nivel == null || b.nivel.isBlank() ? "N2" : b.nivel;
        if (b.donoNome == null || b.donoNome.isBlank()) {
            return new Resultado("REPASSAR", alvo.id(), alvo.titulo(), null, null, nivel, null, null,
                    "Para quem repassar “" + alvo.titulo() + "”?", false, List.of(), null);
        }
        List<PessoaRef> achados = pessoas.buscarPorNome(org, gestorId, b.donoNome);
        if (achados.size() == 1) {
            PessoaRef p = achados.get(0);
            return new Resultado("REPASSAR", alvo.id(), alvo.titulo(), p.id().toString(), p.nome(), nivel, null,
                    null, "Repassar “" + alvo.titulo() + "” para " + p.nome() + ". Confirma?",
                    true, List.of(), null);
        }
        if (achados.size() > 1) {
            List<Candidato> opcoes = achados.stream().limit(3).map(Candidato::de).toList();
            String nomes = opcoes.stream().limit(2).map(Candidato::nome).reduce((a, c) -> a + " ou " + c).orElse("");
            return new Resultado("REPASSAR", alvo.id(), alvo.titulo(), null, b.donoNome, nivel, null, null,
                    "Achei mais de um: " + nomes + ". Qual deles?", false, opcoes, null);
        }
        // Nome não reconhecido: oferece a lista e aprende ao escolher (termoFalado != null).
        List<PessoaRef> equipe = pessoas.listar(org, gestorId);
        if (equipe.isEmpty()) {
            return new Resultado("REPASSAR", alvo.id(), alvo.titulo(), null, b.donoNome, nivel, null, null,
                    "Não encontrei " + b.donoNome + " na sua equipe.", false, List.of(), null);
        }
        return new Resultado("REPASSAR", alvo.id(), alvo.titulo(), null, b.donoNome, nivel, null, null,
                "Não reconheci “" + b.donoNome + "”. Para quem repassar “" + alvo.titulo() + "”?",
                false, equipe.stream().map(Candidato::de).toList(), b.donoNome);
    }

    private static Resultado nada(String frase) {
        return new Resultado("NENHUMA", null, null, null, null, null, null, null, frase, false, List.of(), null);
    }

    // ---- prompt + parsing --------------------------------------------------

    private static String prompt(String texto, List<String> contexto, List<ItemFila> fila) {
        StringBuilder sb = new StringBuilder();
        sb.append("Interprete a fala do gestor no canal de voz da Alçada em UMA intenção.\n");
        sb.append("Intenções: RESOLVER (já feito), REPASSAR (delegar a alguém), ADIAR (deixar pra depois), ");
        sb.append("REGISTRAR (criar lembrete/cobrança), CONSULTAR (pergunta sobre a fila), NENHUMA (não entendi).\n");
        if (fila != null && !fila.isEmpty()) {
            sb.append("Fila atual (use o índice em \"item\" quando a intenção agir sobre um destes):\n");
            for (int i = 0; i < fila.size(); i++) {
                sb.append(i + 1).append(". ").append(fila.get(i).titulo()).append('\n');
            }
        }
        if (contexto != null && !contexto.isEmpty()) {
            sb.append("Conversa recente (mais antiga → mais nova):\n");
            for (String c : contexto) {
                sb.append("- ").append(c).append('\n');
            }
        }
        sb.append("Fala agora: \"").append(texto).append("\"\n");
        sb.append("Regras: item=0 se nenhum/ambíguo. REPASSAR → donoNome (só o nome/apelido da pessoa) e nivel (N1/N2/N3). ");
        sb.append("ADIAR → quandoVoltar. REGISTRAR → tituloNovo. ");
        sb.append("CONSULTAR → reescreva a pergunta COMPLETA em \"pergunta\" usando o contexto ");
        sb.append("(ex.: follow-up \"e para a semana que vem\" → \"o que tenho para a semana que vem\").\n");
        sb.append("Responda só o JSON do schema.");
        return sb.toString();
    }

    private Bruto parse(String conteudo) {
        try {
            JsonNode n = json.readTree(conteudo);
            Bruto b = new Bruto();
            b.intencao = n.path("intencao").asText(null);
            b.item = n.path("item").asInt(0);
            b.donoNome = txt(n, "donoNome");
            b.nivel = txt(n, "nivel");
            b.quandoVoltar = txt(n, "quandoVoltar");
            b.tituloNovo = txt(n, "tituloNovo");
            b.pergunta = txt(n, "pergunta");
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private static String txt(JsonNode n, String campo) {
        return n.hasNonNull(campo) && !n.get(campo).asText().isBlank() ? n.get(campo).asText() : null;
    }

    private static ItemFila item(List<ItemFila> fila, int idx) {
        return (fila != null && idx >= 1 && idx <= fila.size()) ? fila.get(idx - 1) : null;
    }

    /**
     * Item alvo com fallback: se o índice não resolve mas há um único item na
     * fila, é ele (o gestor "esse"/"o reembolso" não tem como ser ambíguo). A
     * confirmação obrigatória (ADR-0014) segue protegendo.
     */
    private static ItemFila alvo(List<ItemFila> fila, int idx) {
        ItemFila i = item(fila, idx);
        if (i == null && fila != null && fila.size() == 1) {
            return fila.get(0);
        }
        return i;
    }

    private static String vazioOu(String v, String padrao) {
        return v == null || v.isBlank() ? padrao : v;
    }

    private static final class Bruto {
        String intencao;
        int item;
        String donoNome;
        String nivel;
        String quandoVoltar;
        String tituloNovo;
        String pergunta;
    }
}
