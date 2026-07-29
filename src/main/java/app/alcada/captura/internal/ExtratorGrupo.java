package app.alcada.captura.internal;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import app.alcada.plataforma.gateway.port.ModelGateway;
import app.alcada.plataforma.gateway.port.Sensibilidade;
import app.alcada.plataforma.gateway.port.Tarefas.Extracao;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaExtracao;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Extrator de compromissos de uma janela de conversa de grupo (024, F2). Espelha
 * {@link Extrator}, mas a unidade é a CONVERSA (não a mensagem): recebe as últimas
 * N mensagens (com o remetente por linha, já minimizadas — ADR-0020 §3) e o token
 * do gestor, e pergunta ao modelo se há algo que <b>depende de uma decisão/ação do
 * gestor</b>. O modelo PROPÕE (INV-10); o chamador decide se vira pendência.
 *
 * <p>Como {@code TarefaExtracao} não tem campo de instrução, ela vai no próprio
 * texto (a janela já vem minimizada; a instrução não tem PII). Saída fora do schema
 * é reprocessada uma vez; indisponibilidade curto-circuita (gateway enfileira).
 */
@ApplicationScoped
public class ExtratorGrupo {

    /** Schema estrito do compromisso (024, ver exemplo-marcello.md). */
    static final String SCHEMA = """
            {"type":"object","additionalProperties":false,
             "required":["dependeDoGestor","tipo","assunto","confianca"],
             "properties":{
               "dependeDoGestor":{"type":"boolean",
                 "description":"true só se há decisão/ação que recai sobre o gestor"},
               "tipo":{"type":"string","enum":["REUNIAO","APROVACAO","DECISAO","FOLLOW_UP","OUTRO"]},
               "assunto":{"type":"string"},
               "quemPede":{"type":["string","null"],"description":"quem pede/espera (token do remetente)"},
               "quando":{"type":["object","null"],"additionalProperties":false,
                 "properties":{
                   "textoOriginal":{"type":["string","null"]},
                   "resolvido":{"type":["string","null"],"description":"ISO-8601 no fuso do tenant"}}},
               "acaoPendente":{"type":["string","null"]},
               "possivelmenteFeito":{"type":"boolean"},
               "confianca":{"type":"number"},
               "sourceMessageSeqs":{"type":"array","items":{"type":"integer"}}}}
            """;

    private static final Set<String> TIPOS =
            Set.of("REUNIAO", "APROVACAO", "DECISAO", "FOLLOW_UP", "OUTRO");

    private static final String INSTRUCAO = """
            Você lê uma JANELA de conversa de GRUPO (uma mensagem por linha, no formato
            "<remetente>: <texto>") e o token do GESTOR. Decida se há algo que DEPENDE de
            uma decisão ou ação do gestor — alguém pedindo a ele, esperando por ele, ou um
            compromisso/decisão que recai sobre ele. Se sim, extraia UM compromisso no schema.
            Se não, responda dependeDoGestor=false e nada mais. NÃO invente dados fora do fio;
            resolva datas relativas ("próxima segunda") pelo fuso do tenant. Responda só o JSON.
            """;

    private final ModelGateway gateway;
    private final ObjectMapper json = new ObjectMapper();

    public ExtratorGrupo(ModelGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * @param ref               id do último evento bruto da janela (rastreio/idempotência)
     * @param janelaMinimizada  as N mensagens, minimizadas, "remetente: texto" por linha
     * @param tokenGestor       token (pseudônimo) do gestor dentro da janela
     * @param rehidratar        devolve os nomes reais nos tokens da saída (local)
     */
    public Optional<Compromisso> extrair(OrgId org, UUID ref, String janelaMinimizada,
                                         String tokenGestor, Function<String, String> rehidratar) {
        String texto = INSTRUCAO + "\n\n=== GESTOR = " + tokenGestor
                + " ===\n=== CONVERSA (grupo) ===\n" + janelaMinimizada;
        Extracao<String> r1 = chamar(org, ref, texto);
        if (r1.confianca() == null) {
            return Optional.empty(); // indisponível: gateway já enfileirou reprocesso
        }
        Optional<Compromisso> c = parse(rehidratar.apply(r1.valor()));
        if (c.isPresent()) {
            return c;
        }
        Extracao<String> r2 = chamar(org, ref, texto); // saída fora do schema → reprocessa uma vez
        if (r2.confianca() == null) {
            return Optional.empty();
        }
        return parse(rehidratar.apply(r2.valor()));
    }

    private Extracao<String> chamar(OrgId org, UUID ref, String texto) {
        return gateway.extrair(new TarefaExtracao<>(
                org, Sensibilidade.INTERNA, ref, texto, SCHEMA, s -> s));
    }

    private Optional<Compromisso> parse(String js) {
        try {
            JsonNode n = json.readTree(js);
            String tipo = texto(n, "tipo");
            String assunto = texto(n, "assunto");
            if (tipo == null || !TIPOS.contains(tipo) || assunto == null) {
                return Optional.empty();
            }
            JsonNode quando = n.path("quando");
            double conf = n.path("confianca").isNumber() ? n.path("confianca").asDouble() : 0.0;
            return Optional.of(new Compromisso(
                    n.path("dependeDoGestor").asBoolean(false),
                    tipo,
                    assunto,
                    texto(n, "quemPede"),
                    quando.isObject() ? texto(quando, "textoOriginal") : null,
                    quando.isObject() ? texto(quando, "resolvido") : null,
                    texto(n, "acaoPendente"),
                    n.path("possivelmenteFeito").asBoolean(false),
                    conf));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n.path(campo);
        return v.isTextual() && !v.asText().isBlank() ? v.asText() : null;
    }
}
