package app.alcada.captura.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
 * Extrator via {@link ModelGateway} (RFC-0007), com schema estrito. Re-hidrata a
 * resposta localmente e valida contra o schema do RFC-0001. Saída inválida é
 * reprocessada uma vez; persistindo, o chamador cria item de baixa confiança.
 * Indisponibilidade curto-circuita (o gateway já enfileirou reprocesso).
 */
@ApplicationScoped
public class Extrator {

    /** Schema estrito (RFC-0001). */
    static final String SCHEMA = """
            {"type":"object","additionalProperties":false,
             "required":["titulo","quem_espera","o_que_trava","classe_sugerida","confianca"],
             "properties":{
               "titulo":{"type":"string"},
               "quem_espera":{"type":"string"},
               "o_que_trava":{"type":"string"},
               "prazo_implicito":{"type":["string","null"]},
               "valor_em_jogo":{"type":["number","null"]},
               "entidades":{"type":"array","items":{"type":"string"}},
               "classe_sugerida":{"type":"string","enum":["DECISAO","BLOQUEIO","ESTEIRA"]},
               "confianca":{"type":"number"}}}
            """;

    private static final Set<String> CLASSES = Set.of("DECISAO", "BLOQUEIO", "ESTEIRA");

    private final ModelGateway gateway;
    private final ObjectMapper json = new ObjectMapper();

    public Extrator(ModelGateway gateway) {
        this.gateway = gateway;
    }

    public Optional<DadosExtraidos> extrair(OrgId org, UUID ref, String textoMinimizado,
                                            Function<String, String> rehidratar) {
        Extracao<String> r1 = chamar(org, ref, textoMinimizado);
        if (r1.confianca() == null) {
            return Optional.empty(); // indisponível: gateway já enfileirou reprocesso
        }
        Optional<DadosExtraidos> d = parse(rehidratar.apply(r1.valor()));
        if (d.isPresent()) {
            return d;
        }
        // saída fora do schema → reprocessa uma vez
        Extracao<String> r2 = chamar(org, ref, textoMinimizado);
        if (r2.confianca() == null) {
            return Optional.empty();
        }
        return parse(rehidratar.apply(r2.valor()));
    }

    private Extracao<String> chamar(OrgId org, UUID ref, String texto) {
        return gateway.extrair(new TarefaExtracao<>(
                org, Sensibilidade.INTERNA, ref, texto, SCHEMA, s -> s));
    }

    private Optional<DadosExtraidos> parse(String js) {
        try {
            JsonNode n = json.readTree(js);
            String titulo = texto(n, "titulo");
            String classe = texto(n, "classe_sugerida");
            if (titulo == null || classe == null || !CLASSES.contains(classe)) {
                return Optional.empty();
            }
            List<String> entidades = new ArrayList<>();
            if (n.path("entidades").isArray()) {
                n.path("entidades").forEach(e -> entidades.add(e.asText()));
            }
            JsonNode valor = n.path("valor_em_jogo");
            BigDecimal valorEmJogo = valor.isNumber() ? valor.decimalValue() : null;
            double conf = n.path("confianca").isNumber() ? n.path("confianca").asDouble() : 1.0;

            return Optional.of(new DadosExtraidos(
                    titulo, texto(n, "quem_espera"), texto(n, "o_que_trava"),
                    texto(n, "prazo_implicito"), valorEmJogo, entidades, classe, conf));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n.path(campo);
        return v.isTextual() && !v.asText().isBlank() ? v.asText() : null;
    }
}
