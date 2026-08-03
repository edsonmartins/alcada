package app.alcada.movel.internal;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import app.alcada.autonomia.port.ContatosExternos;
import app.alcada.autonomia.port.ContatosExternos.ContatoExterno;
import app.alcada.consulta.port.Consulta;
import app.alcada.consulta.port.ResultadoConsulta;
import app.alcada.identidade.port.Pessoas;
import app.alcada.identidade.port.Pessoas.PessoaRef;
import app.alcada.identidade.port.Preferencias;
import app.alcada.plataforma.gateway.port.ModelGateway;
import app.alcada.plataforma.gateway.port.Sensibilidade;
import app.alcada.plataforma.gateway.port.Tarefas.Extracao;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaExtracao;
import app.alcada.plataforma.multitenancy.port.FusoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.triagem.port.Triagem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Interpretação de fala livre por LLM (022, ADR-0014/0019): mapeia a fala do
 * gestor para UMA intenção de um conjunto fechado (INV-10 — o modelo só escolhe,
 * o código executa) e resolve o item da fila, usando o contexto da conversa para
 * follow-ups. No REPASSAR resolve o nome falado contra os dois diretórios — pessoas
 * do tenant ({@link Pessoas}) e contatos externos ({@link ContatosExternos},
 * RFC-0008) —, com memória de apelidos por gestor: quando
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
               "quandoVoltar":{"type":"string"},"tituloNovo":{"type":"string"},"pergunta":{"type":"string"},
               "lembreteQuando":{"type":"string"},"lembreteTexto":{"type":"string"}},
             "required":["intencao"]}""";

    /** Como o assistente fala a data de volta ("quinta, 6, às 10h"). */
    private static final DateTimeFormatter FALA_DATA =
            DateTimeFormatter.ofPattern("EEEE, d, 'às' HH'h'", Locale.of("pt", "BR"));

    private final ModelGateway modelo;
    private final Consulta consulta;
    private final Pessoas pessoas;
    private final ContatosExternos contatos;
    private final Preferencias preferencias;
    private final FusoTenant fuso;
    private final ObjectMapper json = new ObjectMapper();

    public InterpretadorVoz(ModelGateway modelo, Consulta consulta, Pessoas pessoas,
            ContatosExternos contatos, Preferencias preferencias, FusoTenant fuso) {
        this.modelo = modelo;
        this.consulta = consulta;
        this.pessoas = pessoas;
        this.contatos = contatos;
        this.preferencias = preferencias;
        this.fuso = fuso;
    }

    public record ItemFila(String id, String titulo) {
    }

    /**
     * Destino possível do repasse: uma pessoa do tenant ({@code tipo=PESSOA}) ou um
     * contato externo ({@code tipo=CONTATO}, com o {@code canal} por onde será
     * avisado). O app mostra a lista; a escolha é sempre do gestor (INV-10).
     */
    public record Candidato(String id, String nome, String tipo, String canal) {
        static Candidato de(PessoaRef p) {
            return new Candidato(p.id().toString(), p.nome(), "PESSOA", null);
        }

        static Candidato de(ContatoExterno c) {
            return new Candidato(c.id().toString(), c.nome(), "CONTATO", c.canal());
        }
    }

    /**
     * Resultado estruturado para o app: comando a confirmar, consulta respondida,
     * ou nada. {@code termoFalado} != null (com {@code candidatosDono}) sinaliza
     * que o nome não foi reconhecido: se o gestor escolher da lista, o app pede
     * para aprender o apelido (termo→pessoa). No repasse externo (RFC-0008) vem
     * {@code contatoId} + {@code contatoCanal} em vez de {@code donoId} — nunca os
     * dois. {@code podeRegistrarContato} avisa que o app pode oferecer o cadastro
     * do nome falado como contato novo (canal e endereço são coletados lá, não
     * extraídos da fala — o modelo não inventa endereço de terceiro, INV-10).
     * {@code lembreteQuando}/{@code lembreteTexto} vêm no RESOLVER que deixa um
     * compromisso datado (RFC-0009); sem {@code lembreteQuando}, a frase está
     * perguntando a data e o app ainda não pode despachar.
     */
    public record Resultado(
            String intencao, String pendenciaId, String titulo,
            String donoId, String donoNome, String nivel, String tituloNovo,
            String resposta, String frase, boolean precisaConfirmar,
            List<Candidato> candidatosDono, String termoFalado,
            String contatoId, String contatoCanal, boolean podeRegistrarContato,
            String lembreteQuando, String lembreteTexto) {

        /** Resultado sem destino externo (o caso comum das demais intenções). */
        Resultado(String intencao, String pendenciaId, String titulo, String donoId, String donoNome,
                String nivel, String tituloNovo, String resposta, String frase,
                boolean precisaConfirmar, List<Candidato> candidatosDono, String termoFalado) {
            this(intencao, pendenciaId, titulo, donoId, donoNome, nivel, tituloNovo, resposta, frase,
                    precisaConfirmar, candidatosDono, termoFalado, null, null, false, null, null);
        }

        /** Resultado com destino externo (RFC-0008), sem lembrete. */
        Resultado(String intencao, String pendenciaId, String titulo, String donoId, String donoNome,
                String nivel, String tituloNovo, String resposta, String frase,
                boolean precisaConfirmar, List<Candidato> candidatosDono, String termoFalado,
                String contatoId, String contatoCanal, boolean podeRegistrarContato) {
            this(intencao, pendenciaId, titulo, donoId, donoNome, nivel, tituloNovo, resposta, frase,
                    precisaConfirmar, candidatosDono, termoFalado, contatoId, contatoCanal,
                    podeRegistrarContato, null, null);
        }
    }

    public Resultado interpretar(OrgId org, UUID gestorId, String texto, List<String> contexto,
            List<ItemFila> fila) {
        Bruto b;
        try {
            Extracao<Bruto> ex = modelo.extrair(new TarefaExtracao<>(
                    org, Sensibilidade.INTERNA, null,
                    prompt(texto, contexto, fila, OffsetDateTime.now(fuso.fuso(org))),
                    SCHEMA, this::parse));
            b = ex == null ? null : ex.valor();
        } catch (RuntimeException degrada) {
            b = null;
        }
        if (b == null || b.intencao == null) {
            return nada("Não entendi. Pode repetir?");
        }
        return switch (b.intencao) {
            case "CONSULTAR" -> {
                ResultadoConsulta rc = consulta.consultar(org, gestorId, vazioOu(b.pergunta, texto));
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
                if (b.intencao.equals("RESOLVER") && (b.lembreteTexto != null || b.lembreteQuando != null)) {
                    yield resolverComLembrete(org, alvo, b);
                }
                String verbo = b.intencao.equals("RESOLVER") ? "Resolver" : "Adiar";
                yield new Resultado(b.intencao, alvo.id(), alvo.titulo(), null, null, null, null, null,
                        verbo + " “" + alvo.titulo() + "”. Confirma?", true, List.of(), null);
            }
            case "REPASSAR" -> repassar(org, gestorId, b, fila);
            default -> nada("Não entendi. Pode repetir?");
        };
    }

    /**
     * Repasse: precisa de item + destino resolvido. O nome falado é procurado nos
     * dois diretórios — pessoas do tenant e contatos externos (RFC-0008). Um único
     * casamento vira proposta de confirmação; mais de um, ou nenhum, devolve a
     * lista para o gestor escolher (INV-10).
     */
    private Resultado repassar(OrgId org, UUID gestorId, Bruto b, List<ItemFila> fila) {
        ItemFila alvo = alvo(fila, b.item);
        if (alvo == null) {
            return nada("Não identifiquei o item para repassar.");
        }
        // Nível: o que o gestor disse (normalizado); senão o hábito aprendido; senão N2.
        String dito = nivelDito(b.nivel);
        String nivel = dito != null ? dito : preferencias.nivelRepasse(org, gestorId).orElse("N2");
        if (b.donoNome == null || b.donoNome.isBlank()) {
            return new Resultado("REPASSAR", alvo.id(), alvo.titulo(), null, null, nivel, null, null,
                    "Para quem repassar “" + alvo.titulo() + "”?", false, List.of(), null);
        }
        List<PessoaRef> internos = pessoas.buscarPorNome(org, gestorId, b.donoNome);
        List<ContatoExterno> externos = contatos.buscarPorNome(org, b.donoNome);

        if (internos.size() + externos.size() == 1) {
            if (internos.size() == 1) {
                PessoaRef p = internos.get(0);
                return new Resultado("REPASSAR", alvo.id(), alvo.titulo(), p.id().toString(), p.nome(),
                        nivel, null, null, "Repassar “" + alvo.titulo() + "” para " + p.nome() + ". Confirma?",
                        true, List.of(), null);
            }
            ContatoExterno c = externos.get(0);
            return new Resultado("REPASSAR", alvo.id(), alvo.titulo(), null, c.nome(), nivel, null, null,
                    "Repassar “" + alvo.titulo() + "” para " + c.nome() + ". Aviso " + porOnde(c.canal())
                            + ". Confirma?",
                    true, List.of(), null, c.id().toString(), c.canal(), false);
        }
        if (internos.size() + externos.size() > 1) {
            List<Candidato> opcoes = candidatos(internos, externos, 3);
            String nomes = opcoes.stream().limit(2).map(Candidato::nome).reduce((a, c) -> a + " ou " + c).orElse("");
            return new Resultado("REPASSAR", alvo.id(), alvo.titulo(), null, b.donoNome, nivel, null, null,
                    "Achei mais de um: " + nomes + ". Qual deles?", false, opcoes, null);
        }
        // Nome não reconhecido: oferece equipe + contatos conhecidos e aprende ao
        // escolher (termoFalado); e o app pode registrar o nome como contato novo.
        List<Candidato> conhecidos = candidatos(pessoas.listar(org, gestorId), contatos.listar(org), 0);
        String frase = conhecidos.isEmpty()
                ? "Não encontrei " + b.donoNome + ". Quer registrar como contato?"
                : "Não reconheci “" + b.donoNome + "”. Para quem repassar “" + alvo.titulo() + "”?";
        return new Resultado("REPASSAR", alvo.id(), alvo.titulo(), null, b.donoNome, nivel, null, null,
                frase, false, conhecidos, b.donoNome, null, null, true);
    }

    /** Pessoas primeiro, contatos depois; {@code limite} 0 = todos. */
    private static List<Candidato> candidatos(List<PessoaRef> internos, List<ContatoExterno> externos,
            int limite) {
        List<Candidato> todos = new java.util.ArrayList<>(internos.size() + externos.size());
        internos.forEach(p -> todos.add(Candidato.de(p)));
        externos.forEach(c -> todos.add(Candidato.de(c)));
        return limite > 0 && todos.size() > limite ? List.copyOf(todos.subList(0, limite)) : List.copyOf(todos);
    }

    private static String porOnde(String canal) {
        return "EMAIL".equals(canal) ? "por e-mail" : "no WhatsApp";
    }

    /**
     * RESOLVER que deixa um compromisso datado (RFC-0009). O modelo só devolve a
     * data quando consegue resolvê-la; se não veio — ou não sobrevive à validação
     * (passado, longe demais) — o assistente **pergunta**, nunca chuta: um lembrete
     * na data errada é pior que nenhum.
     */
    private Resultado resolverComLembrete(OrgId org, ItemFila alvo, Bruto b) {
        String texto = vazioOu(b.lembreteTexto, alvo.titulo());
        OffsetDateTime quando = dataUtil(b.lembreteQuando);
        if (quando == null) {
            return new Resultado("RESOLVER", alvo.id(), alvo.titulo(), null, null, null, null, null,
                    "Resolver “" + alvo.titulo() + "”. Para quando eu te lembro de " + texto + "?",
                    false, List.of(), null, null, null, false, null, texto);
        }
        return new Resultado("RESOLVER", alvo.id(), alvo.titulo(), null, null, null, null, null,
                "Resolver “" + alvo.titulo() + "” e te lembro " + quandoFalado(org, quando)
                        + ". Confirma?",
                true, List.of(), null, null, null, false, quando.toString(), texto);
    }

    /** Data que sobrevive à validação do domínio; null quando não dá para confiar. */
    private static OffsetDateTime dataUtil(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            OffsetDateTime quando = OffsetDateTime.parse(iso);
            new Triagem.Lembrete(quando, "x").exigirUtil();
            return quando;
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return null;
        }
    }

    /** "quinta, 6, às 10h" — no fuso do tenant, para o gestor conferir de ouvido. */
    private String quandoFalado(OrgId org, OffsetDateTime quando) {
        return FALA_DATA.format(quando.atZoneSameInstant(fuso.fuso(org)));
    }

    private static Resultado nada(String frase) {
        return new Resultado("NENHUMA", null, null, null, null, null, null, null, frase, false, List.of(), null);
    }

    // ---- prompt + parsing --------------------------------------------------

    private static String prompt(String texto, List<String> contexto, List<ItemFila> fila,
            OffsetDateTime agora) {
        StringBuilder sb = new StringBuilder();
        sb.append("Interprete a fala do gestor no canal de voz da Alçada em UMA intenção.\n");
        // O modelo precisa da âncora para resolver "quinta"/"amanhã" (RFC-0009).
        sb.append("Agora é ").append(agora).append(" (fuso do gestor).\n");
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
        sb.append("Regras: item=0 se nenhum/ambíguo. REPASSAR → donoNome (só o nome/apelido de quem ");
        sb.append("recebe, seja da equipe ou de fora; NUNCA telefone ou e-mail); ");
        sb.append("nivel (N1/N2/N3) APENAS se o gestor disser explicitamente — senão OMITA o campo, não invente. ");
        sb.append("ADIAR → quandoVoltar. REGISTRAR → tituloNovo. ");
        sb.append("RESOLVER pode trazer um compromisso que sobrou (\"resolvi, mas marquei reunião ");
        sb.append("quinta 10h\"): \"lembreteTexto\" = o compromisso em poucas palavras e ");
        sb.append("\"lembreteQuando\" = data/hora ISO-8601 COM FUSO, resolvida a partir do agora ");
        sb.append("acima. Se não der para resolver a data com segurança, OMITA lembreteQuando ");
        sb.append("(o assistente pergunta) — nunca invente. ");
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
            b.lembreteQuando = txt(n, "lembreteQuando");
            b.lembreteTexto = txt(n, "lembreteTexto");
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

    /** Nível dito pelo gestor, normalizado para N1/N2/N3. Aceita "n2"/"2"; inválido → null. */
    private static String nivelDito(String s) {
        if (s == null) {
            return null;
        }
        String u = s.trim().toUpperCase().replace(" ", "");
        if (u.matches("N?[123]")) {
            return u.startsWith("N") ? u : "N" + u;
        }
        return null;
    }

    private static final class Bruto {
        String intencao;
        int item;
        String donoNome;
        String nivel;
        String quandoVoltar;
        String tituloNovo;
        String pergunta;
        String lembreteQuando;
        String lembreteTexto;
    }
}
