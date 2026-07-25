package app.alcada.notificacao.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import app.alcada.captura.port.EnviarMensagem;
import app.alcada.notificacao.port.Canal;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Adaptador real do Linktor (ADR-0021). Cliente HTTP simples com serialização
 * explícita — sem SDK pesado (CLAUDE.md §4, native-safe), como o vendax.ai faz.
 *
 * <p><b>Só existe em {@code prod}.</b> Endereça por {@code conversationId} (=
 * {@code EnviarMensagem.responderA}), pois o Linktor só responde a conversa que
 * chegou por inbound (ADR-0025). Falha de entrega vira {@link CanalIndisponivel}
 * — o efeito continua no outbox, reprocessa e, ao esgotar, vira
 * {@code FALHA_COMUNICACAO} pelo varredor de mortos (006).
 */
@ApplicationScoped
@IfBuildProfile("prod")
public class LinktorHttp implements Canal {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http;
    private final String baseUrl;
    private final Optional<String> apiKey;

    @Inject
    public LinktorHttp(
            @ConfigProperty(name = "linktor.api.url", defaultValue = "https://api.linktor.dev") String baseUrl,
            @ConfigProperty(name = "linktor.api.key") Optional<String> apiKey) {
        this(baseUrl, apiKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    /** Construtor testável (permite injetar um HttpClient apontando para servidor local). */
    LinktorHttp(String baseUrl, Optional<String> apiKey, HttpClient http) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.http = http;
    }

    @Override
    public boolean enviar(OrgId org, EnviarMensagem m) {
        if (apiKey.isEmpty()) {
            throw new CanalIndisponivel("Linktor sem API key configurada");
        }
        String conversationId = m.responderA();
        if (conversationId == null || conversationId.isBlank()) {
            // não deveria chegar aqui: o Despachante já registra COMUNICACAO_IMPOSSIVEL antes
            throw new CanalIndisponivel("sem conversationId para responder");
        }

        String url = baseUrl + "/api/v1/conversations/" + conversationId + "/messages";
        String corpo = montarCorpo(m);
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url))
                    .header("X-API-Key", apiKey.get())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(corpo))
                    .build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 == 2) {
                return true;
            }
            // 4xx/5xx → indisponível; o outbox reprocessa (ADR-0025, decisão 5)
            throw new CanalIndisponivel("Linktor respondeu " + r.statusCode());
        } catch (CanalIndisponivel e) {
            throw e;
        } catch (Exception e) {
            throw new CanalIndisponivel("Linktor indisponível: " + e.getMessage());
        }
    }

    private String montarCorpo(EnviarMensagem m) {
        ObjectNode raiz = json.createObjectNode();
        raiz.put("text", m.texto());
        ObjectNode meta = raiz.putObject("metadata");
        meta.put("source", "alcada");
        meta.put("idempotency_key", m.idempotencyKey());
        try {
            return json.writeValueAsString(raiz);
        } catch (Exception e) {
            throw new CanalIndisponivel("falha ao serializar mensagem: " + e.getMessage());
        }
    }
}
