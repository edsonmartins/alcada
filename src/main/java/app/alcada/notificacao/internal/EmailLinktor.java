package app.alcada.notificacao.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import app.alcada.notificacao.port.Email;
import app.alcada.notificacao.port.EnviarEmail;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Envia avisos de repasse pelo canal EMAIL declarado no Linktor do tenant. */
@ApplicationScoped
@IfBuildProperty(name = "linktor.email.real", stringValue = "true")
public class EmailLinktor implements Email {
    private final EntityManager em;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http;
    private final String baseUrl;
    private final Optional<String> apiKey;

    @Inject
    public EmailLinktor(EntityManager em,
            @ConfigProperty(name = "linktor.api.url", defaultValue = "https://api.linktor.dev") String baseUrl,
            @ConfigProperty(name = "linktor.api.key") Optional<String> apiKey) {
        this(em, baseUrl, apiKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    EmailLinktor(EntityManager em, String baseUrl, Optional<String> apiKey, HttpClient http) {
        this.em = em;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.http = http;
    }

    @Override
    public boolean enviar(OrgId org, EnviarEmail m) {
        if (apiKey.isEmpty()) throw new Email.EmailIndisponivel("Linktor sem API key configurada");
        String channel = canalEmail(org);
        if (channel == null) throw new Email.EmailIndisponivel("tenant sem canal EMAIL no Linktor");
        ObjectNode body = json.createObjectNode();
        body.put("channel_id", channel);
        body.put("to", m.to());
        body.put("content_type", "text");
        body.put("text", m.texto());
        ObjectNode metadata = body.putObject("metadata");
        metadata.put("source", "alcada");
        metadata.put("idempotency_key", m.idempotencyKey());
        if (m.correlacao() != null) metadata.put("alcada_correlation", m.correlacao());
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                    URI.create(baseUrl + "/api/v1/messages/send"))
                    .header("X-API-Key", apiKey.get())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) return true;
            throw new Email.EmailIndisponivel("Linktor respondeu " + response.statusCode());
        } catch (Email.EmailIndisponivel e) {
            throw e;
        } catch (Exception e) {
            throw new Email.EmailIndisponivel("Linktor indisponível: " + e.getMessage());
        }
    }

    private String canalEmail(OrgId org) {
        @SuppressWarnings("unchecked")
        var rows = em.createNativeQuery("SELECT linktor_channel_id FROM fonte "
                + "WHERE org_id=? AND tipo='EMAIL' AND ativa=true AND linktor_channel_id IS NOT NULL "
                + "ORDER BY id LIMIT 1").setParameter(1, org.valor()).getResultList();
        return rows.isEmpty() ? null : (String) rows.getFirst();
    }
}
