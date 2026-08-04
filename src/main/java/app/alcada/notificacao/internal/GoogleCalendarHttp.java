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
import java.util.UUID;

import app.alcada.notificacao.port.Calendario;
import app.alcada.notificacao.port.ContasCalendario;
import app.alcada.notificacao.port.ContasCalendario.Conta;
import app.alcada.notificacao.port.CriarEvento;
import app.alcada.plataforma.multitenancy.port.OrgId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Google Calendar pela API HTTP (RFC-0009 F2.3b). Cliente simples com
 * serialização explícita — sem SDK (CLAUDE.md §4: reflexão é inimiga do native
 * image). <b>Só existe em {@code prod}</b>; fora dele vale o {@link CalendarioStub},
 * para dev/test nunca tocarem a agenda de ninguém.
 *
 * <p>O access token vive pouco: quando vencido, renova pelo refresh token antes
 * de chamar. Falha de rede/5xx vira {@link CalendarioIndisponivel} (o outbox
 * reprocessa); 401 após renovar e ausência de conta viram {@link SemConta} —
 * repetir não resolveria, o gestor precisa reconectar.
 */
@ApplicationScoped
@IfBuildProfile("prod")
public class GoogleCalendarHttp implements Calendario {

    private static final String API = "https://www.googleapis.com/calendar/v3/calendars/primary/events";
    private static final String TOKEN = "https://oauth2.googleapis.com/token";

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http;
    private final ContasCalendarioJdbc contas;
    private final Optional<String> clientId;
    private final Optional<String> clientSecret;

    @Inject
    public GoogleCalendarHttp(ContasCalendarioJdbc contas,
            @ConfigProperty(name = "alcada.google.client-id") Optional<String> clientId,
            @ConfigProperty(name = "alcada.google.client-secret") Optional<String> clientSecret) {
        this(contas, clientId, clientSecret,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    GoogleCalendarHttp(ContasCalendarioJdbc contas, Optional<String> clientId,
            Optional<String> clientSecret, HttpClient http) {
        this.contas = contas;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.http = http;
    }

    @Override
    public String criarEvento(OrgId org, CriarEvento e) {
        String token = tokenValido(org, e.gestorId());
        String corpo = """
                {"summary":%s,"start":{"dateTime":"%s"},"end":{"dateTime":"%s"},\
                "reminders":{"useDefault":true},"source":{"title":"Alçada"}}"""
                .formatted(texto(e.titulo()), e.quando(), e.quando().plus(e.duracao()));
        HttpResponse<String> r = enviar(HttpRequest.newBuilder(URI.create(API))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8)), e.gestorId());
        if (r.statusCode() / 100 != 2) {
            throw falha(r, e.gestorId());
        }
        try {
            JsonNode n = json.readTree(r.body());
            return n.path("id").asText(null);
        } catch (Exception ex) {
            throw new CalendarioIndisponivel("resposta ilegível do Google: " + ex.getMessage());
        }
    }

    @Override
    public void cancelarEvento(OrgId org, UUID gestorId, String eventoId) {
        String token = tokenValido(org, gestorId);
        HttpResponse<String> r = enviar(HttpRequest
                .newBuilder(URI.create(API + "/" + URLEncoder.encode(eventoId, StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(30))
                .DELETE(), gestorId);
        // 404/410: o evento já não está lá — o estado desejado é justamente esse.
        if (r.statusCode() / 100 != 2 && r.statusCode() != 404 && r.statusCode() != 410) {
            throw falha(r, gestorId);
        }
    }

    // ---- token -------------------------------------------------------------

    /** Access token do gestor, renovado se vencido. */
    private String tokenValido(OrgId org, UUID gestorId) {
        Conta conta = contas.doGestor(org, gestorId)
                .orElseThrow(() -> new SemConta("gestor sem calendário conectado: " + gestorId));
        if (!conta.vencido()) {
            return conta.accessToken();
        }
        if (conta.refreshToken() == null) {
            throw new SemConta("token vencido e sem refresh: reconecte o calendário");
        }
        return renovar(org, gestorId, conta.refreshToken());
    }

    private String renovar(OrgId org, UUID gestorId, String refreshToken) {
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            throw new CalendarioIndisponivel("Google sem client-id/secret configurados");
        }
        String form = "client_id=" + enc(clientId.get())
                + "&client_secret=" + enc(clientSecret.get())
                + "&refresh_token=" + enc(refreshToken)
                + "&grant_type=refresh_token";
        HttpResponse<String> r = enviar(HttpRequest.newBuilder(URI.create(TOKEN))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(form)), gestorId);
        if (r.statusCode() / 100 != 2) {
            // refresh recusado = consentimento revogado do lado do Google.
            throw new SemConta("Google recusou renovar o token (" + r.statusCode() + ")");
        }
        try {
            JsonNode n = json.readTree(r.body());
            String acesso = n.path("access_token").asText(null);
            long segundos = n.path("expires_in").asLong(3600);
            OffsetDateTime expira = OffsetDateTime.now().plusSeconds(segundos);
            contas.atualizarAcesso(org, gestorId, acesso, expira);
            return acesso;
        } catch (Exception ex) {
            throw new CalendarioIndisponivel("renovação ilegível: " + ex.getMessage());
        }
    }

    private HttpResponse<String> enviar(HttpRequest.Builder req, UUID gestorId) {
        try {
            return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CalendarioIndisponivel("interrompido ao falar com o Google");
        } catch (Exception e) {
            throw new CalendarioIndisponivel("Google indisponível: " + e.getMessage());
        }
    }

    private static RuntimeException falha(HttpResponse<String> r, UUID gestorId) {
        if (r.statusCode() == 401 || r.statusCode() == 403) {
            return new SemConta("Google recusou o acesso do gestor " + gestorId
                    + " (" + r.statusCode() + "): reconecte o calendário");
        }
        return new CalendarioIndisponivel("Google respondeu " + r.statusCode());
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String texto(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
