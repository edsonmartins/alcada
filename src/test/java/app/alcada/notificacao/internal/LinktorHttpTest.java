package app.alcada.notificacao.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import app.alcada.captura.port.EnviarMensagem;
import app.alcada.captura.port.EnviarDireto;
import app.alcada.notificacao.port.Canal;
import app.alcada.plataforma.multitenancy.port.OrgId;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

/**
 * Testa o adaptador REAL do Linktor contra um servidor HTTP em processo — sem
 * host externo, sem CDI (o bean é gated a {@code prod}). Verifica o contrato de
 * requisição e o mapeamento de falha para {@link Canal.CanalIndisponivel}.
 */
class LinktorHttpTest {

    @Test
    void envia_para_a_conversa_com_x_api_key_e_idempotencia() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            path.set(ex.getRequestURI().getPath());
            apiKey.set(ex.getRequestHeaders().getFirst("X-API-Key"));
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            responder(ex, 201, "{\"data\":{\"id\":\"m1\"}}");
        });
        server.start();
        try {
            Canal linktor = new LinktorHttp(base(server), Optional.of("lk_teste"), HttpClient.newHttpClient());
            boolean ok = linktor.enviar(new OrgId(UUID.randomUUID()), new EnviarMensagem(
                    "WHATSAPP", "rafael", "Sua solicitação foi resolvida.", "conv-123", "chave-1"));

            assertTrue(ok);
            assertEquals("/api/v1/conversations/conv-123/messages", path.get());
            assertEquals("lk_teste", apiKey.get());
            assertTrue(body.get().contains("\"content_type\":\"text\""));
            assertTrue(body.get().contains("\"content\":\"Sua solicitação foi resolvida.\""));
            assertTrue(body.get().contains("\"idempotency_key\":\"chave-1\""));
            assertTrue(body.get().contains("\"source\":\"alcada\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void envio_direto_propaga_correlacao_opaca_no_metadata() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            responder(ex, 202, "{\"success\":true,\"data\":{\"id\":\"m2\",\"status\":\"queued\"}}");
        });
        server.start();
        try {
            Canal linktor = new LinktorHttp(base(server), Optional.of("lk_teste"), HttpClient.newHttpClient());
            assertTrue(linktor.enviarDireto(new OrgId(UUID.randomUUID()),
                    new EnviarDireto("ch-1", "+5511999999999", "repasse", "k-2", "token-opaco")));
            assertTrue(body.get().contains("\"channel_id\":\"ch-1\""));
            assertTrue(body.get().contains("\"to\":\"+5511999999999\""));
            assertTrue(body.get().contains("\"content_type\":\"text\""));
            assertTrue(body.get().contains("\"text\":\"repasse\""));
            assertTrue(body.get().contains("\"alcada_correlation\":\"token-opaco\""));
            assertTrue(body.get().contains("\"idempotency_key\":\"k-2\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void linktor_fora_do_ar_5xx_vira_canal_indisponivel() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> responder(ex, 503, "indisponível"));
        server.start();
        try {
            Canal linktor = new LinktorHttp(base(server), Optional.of("lk_teste"), HttpClient.newHttpClient());
            assertThrows(Canal.CanalIndisponivel.class, () -> linktor.enviar(
                    new OrgId(UUID.randomUUID()),
                    new EnviarMensagem("WHATSAPP", "rafael", "oi", "conv-9", "k")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reserva_idempotente_em_andamento_retorna_para_retry_do_outbox() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> responder(ex, 409, "{\"error\":\"idempotency in progress\"}"));
        server.start();
        try {
            Canal linktor = new LinktorHttp(base(server), Optional.of("lk_teste"), HttpClient.newHttpClient());
            assertThrows(Canal.CanalIndisponivel.class, () -> linktor.enviarDireto(
                    new OrgId(UUID.randomUUID()), new EnviarDireto("ch-1", "+5511999999999", "oi", "k", null)));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sem_api_key_e_indisponivel() {
        Canal linktor = new LinktorHttp("http://127.0.0.1:1", Optional.empty(), HttpClient.newHttpClient());
        assertThrows(Canal.CanalIndisponivel.class, () -> linktor.enviar(
                new OrgId(UUID.randomUUID()), new EnviarMensagem("WHATSAPP", "x", "oi", "conv", "k")));
    }

    private static String base(HttpServer s) {
        return "http://127.0.0.1:" + s.getAddress().getPort();
    }

    private static void responder(com.sun.net.httpserver.HttpExchange ex, int status, String corpo) throws IOException {
        byte[] b = corpo.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }
}
