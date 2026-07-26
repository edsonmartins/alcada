package app.alcada.plataforma.gateway.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Transporte real ao OpenRouter — cliente HTTP simples com serialização
 * explícita (CLAUDE.md §4: reflexão é o inimigo do native image; nada de SDK
 * pesado). A política fixa e {@code response_format: json_schema} entram no
 * corpo; os plugins do OpenRouter ficam desabilitados.
 *
 * <p><b>Ativado por {@code gateway.openrouter.enabled=true}</b> (ligado nos
 * profiles {@code prod} e {@code demo} — ver application.properties). Dev/test
 * usam o {@link TransporteStub}, que nunca bate no host externo. No piloto
 * (demo), a chamada real ainda depende da chave ({@code OPENROUTER_API_KEY}) e a
 * minimização/roteamento por sensibilidade continuam valendo (ADR-0010/0020):
 * classe RESTRITA nunca sai (vai para inferência local).
 */
@ApplicationScoped
@IfBuildProperty(name = "gateway.openrouter.enabled", stringValue = "true")
public class TransporteHttp implements TransporteModelo {

    @ConfigProperty(name = "gateway.openrouter.url",
            defaultValue = "https://openrouter.ai/api/v1/chat/completions")
    String url;

    @ConfigProperty(name = "gateway.openrouter.audio-url",
            defaultValue = "https://openrouter.ai/api/v1/audio/transcriptions")
    String audioUrl;

    @ConfigProperty(name = "gateway.openrouter.api-key")
    Optional<String> apiKey;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public Resposta enviar(Requisicao req) {
        if (apiKey.isEmpty()) {
            return Resposta.erro(Status.INDISPONIVEL); // sem credencial: indisponível, não degrada
        }
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey.get())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(montarCorpo(req)))
                    .build();
            HttpResponse<String> r = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) {
                JsonNode raiz = json.readTree(r.body());
                String conteudo = raiz.path("choices").path(0).path("message").path("content").asText("");
                int in = raiz.path("usage").path("prompt_tokens").asInt(0);
                int out = raiz.path("usage").path("completion_tokens").asInt(0);
                return Resposta.ok(conteudo, in, out);
            }
            String corpo = r.body() == null ? "" : r.body().toLowerCase();
            if (corpo.contains("json_schema") || corpo.contains("response_format")) {
                return Resposta.erro(Status.SEM_SUPORTE_SCHEMA);
            }
            if (r.statusCode() >= 500) {
                return Resposta.erro(Status.INDISPONIVEL);
            }
            return Resposta.erro(Status.GUARDRAIL_RECUSOU);
        } catch (Exception e) {
            return Resposta.erro(Status.INDISPONIVEL);
        }
    }

    @Override
    public Resposta transcrever(RequisicaoAudio req) {
        if (apiKey.isEmpty()) {
            return Resposta.erro(Status.INDISPONIVEL);
        }
        try {
            ObjectNode raiz = json.createObjectNode();
            raiz.put("model", req.modelo());
            if (req.idioma() != null && !req.idioma().isBlank()) {
                raiz.put("language", req.idioma());
            }
            ObjectNode ia = raiz.putObject("input_audio");
            ia.put("data", req.audioBase64());
            ia.put("format", req.formato());
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(audioUrl))
                    .header("Authorization", "Bearer " + apiKey.get())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(raiz)))
                    .build();
            HttpResponse<String> r = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) {
                JsonNode raizR = json.readTree(r.body());
                String texto = raizR.path("text").asText("");
                int seg = raizR.path("usage").path("seconds").asInt(0);
                return Resposta.ok(texto, seg, 0); // tokensIn reaproveitado como segundos de áudio
            }
            return Resposta.erro(Status.INDISPONIVEL);
        } catch (Exception e) {
            return Resposta.erro(Status.INDISPONIVEL);
        }
    }

    private String montarCorpo(Requisicao req) throws Exception {
        ObjectNode raiz = json.createObjectNode();
        raiz.put("model", req.modelo());

        ArrayNode messages = raiz.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", req.texto());

        if (req.schemaJson() != null) {
            ObjectNode rf = raiz.putObject("response_format");
            rf.put("type", "json_schema");
            ObjectNode js = rf.putObject("json_schema");
            js.put("name", "resposta"); // OpenRouter EXIGE 'name' no json_schema
            js.put("strict", true);
            js.set("schema", json.readTree(req.schemaJson()));
        }

        PoliticaProvedor p = req.politica();
        ObjectNode provider = raiz.putObject("provider");
        ArrayNode only = provider.putArray("only");
        p.only().forEach(only::add);
        provider.put("allow_fallbacks", p.allowFallbacks());
        provider.put("data_collection", p.dataCollection());
        provider.put("zdr", p.zdr());
        provider.put("require_parameters", p.requireParameters());

        raiz.putArray("plugins"); // plugins/ferramentas desabilitados (ADR-0020)
        return json.writeValueAsString(raiz);
    }
}
