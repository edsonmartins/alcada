package app.alcada.notificacao.internal;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import app.alcada.notificacao.port.ContasCalendario.Conta;
import app.alcada.notificacao.port.OauthCalendario;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Troca de código por tokens no Google (RFC-0009 F2.3b). Só em {@code prod} — em
 * dev/test vale o {@link OauthCalendarioStub}, que não fala com ninguém.
 */
@ApplicationScoped
@IfBuildProfile("prod")
public class GoogleOauthHttp implements OauthCalendario {

    private static final String TOKEN = "https://oauth2.googleapis.com/token";

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http;
    private final Optional<String> clientId;
    private final Optional<String> clientSecret;

    @Inject
    public GoogleOauthHttp(
            @ConfigProperty(name = "alcada.google.client-id") Optional<String> clientId,
            @ConfigProperty(name = "alcada.google.client-secret") Optional<String> clientSecret) {
        this(clientId, clientSecret,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    GoogleOauthHttp(Optional<String> clientId, Optional<String> clientSecret, HttpClient http) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.http = http;
    }

    @Override
    public Conta trocar(String codigo, String redirectUri) {
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            throw new ConsentimentoInvalido("Google sem client-id/secret configurados");
        }
        String form = "code=" + enc(codigo)
                + "&client_id=" + enc(clientId.get())
                + "&client_secret=" + enc(clientSecret.get())
                + "&redirect_uri=" + enc(redirectUri)
                + "&grant_type=authorization_code";
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(TOKEN))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) {
                throw new ConsentimentoInvalido("Google recusou o código (" + r.statusCode() + ")");
            }
            JsonNode n = json.readTree(r.body());
            String acesso = n.path("access_token").asText(null);
            if (acesso == null) {
                throw new ConsentimentoInvalido("Google não devolveu access_token");
            }
            return new Conta("GOOGLE", acesso,
                    n.path("refresh_token").asText(null),
                    OffsetDateTime.now().plusSeconds(n.path("expires_in").asLong(3600)),
                    n.path("scope").asText(null));
        } catch (ConsentimentoInvalido e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConsentimentoInvalido("interrompido ao falar com o Google");
        } catch (Exception e) {
            throw new ConsentimentoInvalido("falha ao trocar o código: " + e.getMessage());
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
