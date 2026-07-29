package app.alcada.captura;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** Webhook de entrada do Linktor: HMAC fail-closed, replay e reentrega idempotente. */
@QuarkusTest
class LinktorInboundTest {

    private static final String SEGREDO = "segredo-do-canal";

    @Inject EntityManager em;

    @Test
    void assinatura_valida_ingere_a_mensagem() {
        OrgId org = novaOrg();
        criarFonteLinktor(org, "CH-1");
        String body = envelope("MSG-1", "conv-1");
        long ts = Instant.now().getEpochSecond();

        given().header("X-Linktor-Signature", hmac(SEGREDO, ts + "." + body))
                .header("X-Linktor-Timestamp", String.valueOf(ts))
                .contentType("application/json").body(body)
        .when().post("/v1/captura/linktor")
        .then().statusCode(200);

        assertEquals(1L, contar(org, "SELECT count(*) FROM evento_bruto WHERE org_id = ? AND mensagem_id = 'MSG-1'"));
    }

    @Test
    void assinatura_invalida_e_rejeitada() {
        OrgId org = novaOrg();
        criarFonteLinktor(org, "CH-2");
        String body = envelope("MSG-2", "conv-2");
        long ts = Instant.now().getEpochSecond();

        given().header("X-Linktor-Signature", "0000")   // assinatura errada
                .header("X-Linktor-Timestamp", String.valueOf(ts))
                .contentType("application/json").body(body)
        .when().post("/v1/captura/linktor")
        .then().statusCode(401);

        assertEquals(0L, contar(org, "SELECT count(*) FROM evento_bruto WHERE org_id = ? AND mensagem_id = 'MSG-2'"));
    }

    @Test
    void timestamp_fora_da_janela_e_rejeitado_replay() {
        OrgId org = novaOrg();
        criarFonteLinktor(org, "CH-3");
        String body = envelope("MSG-3", "conv-3");
        long velho = Instant.now().getEpochSecond() - 400; // > 300s

        given().header("X-Linktor-Signature", hmac(SEGREDO, velho + "." + body))
                .header("X-Linktor-Timestamp", String.valueOf(velho))
                .contentType("application/json").body(body)
        .when().post("/v1/captura/linktor")
        .then().statusCode(401);

        assertEquals(0L, contar(org, "SELECT count(*) FROM evento_bruto WHERE org_id = ? AND mensagem_id = 'MSG-3'"));
    }

    @Test
    void reentrega_do_mesmo_message_id_e_no_op_limpo() {
        OrgId org = novaOrg();
        criarFonteLinktor(org, "CH-4");
        String body = envelope("MSG-4", "conv-4");
        long ts = Instant.now().getEpochSecond();
        String sig = hmac(SEGREDO, ts + "." + body);

        for (int i = 0; i < 2; i++) {
            given().header("X-Linktor-Signature", sig).header("X-Linktor-Timestamp", String.valueOf(ts))
                    .contentType("application/json").body(body)
            .when().post("/v1/captura/linktor")
            .then().statusCode(200); // reentrega não dá 500 (senão o webhook entra em loop)
        }
        assertEquals(1L, contar(org, "SELECT count(*) FROM evento_bruto WHERE org_id = ? AND mensagem_id = 'MSG-4'"));
    }

    @Test
    void grupo_selecionado_ingere_marca_grupo_e_threadeia_pelo_grupo() {
        OrgId org = novaOrg();
        criarFonteLinktor(org, "CH-GRP");
        String grupoId = "120363000000000000@g.us";
        ativarGrupo(org, grupoId); // opt-in: o gestor selecionou este grupo
        String body = envelopeGrupo("MSG-G", grupoId);
        long ts = Instant.now().getEpochSecond();

        given().header("X-Linktor-Signature", hmac(SEGREDO, ts + "." + body))
                .header("X-Linktor-Timestamp", String.valueOf(ts))
                .contentType("application/json").body(body)
        .when().post("/v1/captura/linktor")
        .then().statusCode(200);

        assertEquals(1L, contar(org,
                "SELECT count(*) FROM evento_bruto WHERE org_id = ? AND mensagem_id = 'MSG-G'"
                        + " AND grupo AND thread_ref = '" + grupoId + "'"),
                "grupo selecionado: ingere, marca grupo e thread pelo grupo (chat_jid)");
    }

    // C13 — só grupos selecionados são acompanhados (opt-in, ADR-0011 §1).
    @Test
    void grupo_nao_selecionado_e_descartado_mas_descoberto() {
        OrgId org = novaOrg();
        criarFonteLinktor(org, "CH-GRP2");
        String grupoId = "120363999999999999@g.us";
        String body = envelopeGrupo("MSG-G2", grupoId);
        long ts = Instant.now().getEpochSecond();

        given().header("X-Linktor-Signature", hmac(SEGREDO, ts + "." + body))
                .header("X-Linktor-Timestamp", String.valueOf(ts))
                .contentType("application/json").body(body)
        .when().post("/v1/captura/linktor")
        .then().statusCode(200);

        assertEquals(0L, contar(org,
                "SELECT count(*) FROM evento_bruto WHERE org_id = ? AND mensagem_id = 'MSG-G2'"),
                "grupo não selecionado: conteúdo NÃO é ingerido");
        assertEquals(1L, contar(org,
                "SELECT count(*) FROM grupo_acompanhado WHERE org_id = ? AND grupo_id = '" + grupoId
                        + "' AND NOT ativa"),
                "mas o grupo é descoberto (inativo) para o gestor poder selecionar");
    }

    // ---- helpers -----------------------------------------------------------

    private static String envelope(String messageId, String conversationId) {
        return "{\"id\":\"evt\",\"type\":\"message.received\",\"tenantId\":\"t\",\"environment\":\"prod\","
                + "\"data\":{\"channelId\":\"" + canalDoTeste + "\",\"channelType\":\"whatsapp\","
                + "\"conversationId\":\"" + conversationId + "\",\"contactId\":\"c1\","
                + "\"message\":{\"id\":\"" + messageId + "\",\"content\":{\"text\":\"@alcada aprovar\"},"
                + "\"metadata\":{\"phone\":\"5544\"},\"senderId\":\"s1\"}}}";
    }

    private static String envelopeGrupo(String messageId, String grupoId) {
        return "{\"id\":\"evt\",\"type\":\"message.received\",\"tenantId\":\"t\",\"environment\":\"prod\","
                + "\"data\":{\"channelId\":\"" + canalDoTeste + "\",\"channelType\":\"whatsapp\","
                + "\"conversationId\":\"conv-g\",\"contactId\":\"c1\","
                + "\"group\":{\"id\":\"" + grupoId + "\"},"
                + "\"message\":{\"id\":\"" + messageId + "\",\"content\":{\"text\":\"vamos marcar reunião?\"},"
                + "\"metadata\":{\"phone\":\"5544\"},\"senderId\":\"s1\"}}}";
    }

    private static String canalDoTeste; // setado por criarFonteLinktor

    private void criarFonteLinktor(OrgId org, String channelId) {
        canalDoTeste = channelId;
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO fonte (id, org_id, tipo, identificador, segredo, linktor_channel_id)
                VALUES (?, ?, 'WHATSAPP', 'grupo', ?, ?)
                """)
                .setParameter(1, UUID.randomUUID()).setParameter(2, org.valor())
                .setParameter(3, SEGREDO).setParameter(4, channelId).executeUpdate());
    }

    private void ativarGrupo(OrgId org, String grupoId) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO grupo_acompanhado (org_id, fonte_id, grupo_id, ativa)
                SELECT ?, id, ?, true FROM fonte WHERE linktor_channel_id = ?
                ON CONFLICT (fonte_id, grupo_id) DO UPDATE SET ativa = true
                """)
                .setParameter(1, org.valor()).setParameter(2, grupoId)
                .setParameter(3, canalDoTeste).executeUpdate());
    }

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    private long contar(OrgId org, String sql) {
        return ((Number) QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery(sql).setParameter(1, org.valor()).getSingleResult())).longValue();
    }

    private static String hmac(String segredo, String msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] d = mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
