package app.alcada.captura.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import app.alcada.captura.port.MensagemRecebida;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

/**
 * Webhook de entrada do Linktor (ADR-0021). Fail-closed por HMAC-SHA256 sobre o
 * body cru (assinatura por fonte, INV-15). Mapeia o envelope
 * {@code message.received} para {@link MensagemRecebida} e ingere (idempotente
 * por {@code message.id}). O segredo da fonte é credencial — nunca em log/erro.
 */
@Path("/v1/captura/linktor")
public class LinktorWebhookResource {

    private static final Logger LOG = Logger.getLogger(LinktorWebhookResource.class);

    private final EntityManager em;
    private final Ingestao ingestao;
    private final ObjectMapper json = new ObjectMapper();

    public LinktorWebhookResource(EntityManager em, Ingestao ingestao) {
        this.em = em;
        this.ingestao = ingestao;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response receber(@HeaderParam("X-Linktor-Signature") String assinatura,
                            @HeaderParam("X-Linktor-Timestamp") String timestamp,
                            String body) {
        JsonNode envelope;
        try {
            envelope = json.readTree(body == null ? "" : body);
        } catch (Exception e) {
            return Response.status(400).build();
        }

        JsonNode data = envelope.path("data");
        String channelId = texto(data, "channelId");
        if (channelId == null) {
            return Response.status(401).build(); // sem canal identificável — não confirma existência
        }

        // resolve a fonte pelo canal do Linktor → org + segredo (credencial)
        Fonte fonte = fontePorCanal(channelId);
        if (fonte == null) {
            LOG.warnf("webhook Linktor para canal desconhecido %s", channelId);
            return Response.status(401).build();
        }

        if (!AssinaturaHmac.valida(fonte.segredo, timestamp, body, assinatura, Instant.now().getEpochSecond())) {
            LOG.warnf("assinatura inválida para fonte %s", fonte.id); // nunca o segredo
            return Response.status(401).build();
        }

        // só tratamos mensagens recebidas; outros eventos → 200 sem ação (evita retry-storm)
        if (!"message.received".equals(texto(envelope, "type"))) {
            return Response.ok().build();
        }

        JsonNode msg = data.path("message");
        String mensagemId = texto(msg, "id");
        if (mensagemId == null) {
            return Response.status(400).build(); // sem id não há idempotência
        }

        // Grupo (024): o Linktor envia `data.group.id` quando a mensagem veio de um
        // grupo (ausente em 1:1). Threadeamos pelo grupo; autor segue o indivíduo.
        JsonNode grupoNode = data.path("group");
        String grupoId = grupoNode.isMissingNode() || grupoNode.isNull() ? null : texto(grupoNode, "id");
        boolean grupo = grupoId != null && !grupoId.isBlank();
        String threadRef = grupo ? grupoId : texto(data, "conversationId");

        // Seleção de grupos (024 F1b, ADR-0011 §1): descobre o grupo (só metadados:
        // id/nome) e só ingere o conteúdo se o gestor o selecionou (ativa). Grupo não
        // selecionado → descarta (200, sem evento_bruto).
        if (grupo && !grupoAtivo(fonte, grupoId, texto(grupoNode, "name"))) {
            return Response.ok().build();
        }

        MensagemRecebida m = new MensagemRecebida(
                upper(texto(data, "channelType")),
                fonte.id.toString(),
                autor(data, msg),
                threadRef,
                textoMensagem(msg),
                List.of(),
                mensagemId,
                grupo,
                grupoId);

        // idempotente: reentrega do mesmo message.id → no-op limpo (não 500)
        ingestao.ingerir(new OrgId(fonte.orgId), m);
        return Response.ok().build();
    }

    private record Fonte(UUID id, UUID orgId, String segredo) {
    }

    private Fonte fontePorCanal(String channelId) {
        try {
            Object[] r = (Object[]) em.createNativeQuery(
                    "SELECT id, org_id, segredo FROM fonte WHERE linktor_channel_id = ? AND ativa")
                    .setParameter(1, channelId).getSingleResult();
            return new Fonte((UUID) r[0], (UUID) r[1], (String) r[2]);
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Descobre o grupo (upsert de metadados — id/nome/último visto, sem conteúdo) e
     * diz se está SELECIONADO (ativa). Opt-in do gestor (ADR-0011 §1): grupo novo
     * nasce inativo; o conteúdo só é ingerido quando ele ativa em /v1/grupos.
     */
    private boolean grupoAtivo(Fonte fonte, String grupoId, String nome) {
        em.createNativeQuery("""
                INSERT INTO grupo_acompanhado (org_id, fonte_id, grupo_id, nome, ultimo_visto)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (fonte_id, grupo_id) DO UPDATE
                    SET ultimo_visto = now(),
                        nome = COALESCE(EXCLUDED.nome, grupo_acompanhado.nome)
                """)
                .setParameter(1, fonte.orgId())
                .setParameter(2, fonte.id())
                .setParameter(3, grupoId)
                .setParameter(4, nome == null || nome.isBlank() ? null : nome)
                .executeUpdate();
        Boolean ativa = (Boolean) em.createNativeQuery(
                "SELECT ativa FROM grupo_acompanhado WHERE fonte_id = ? AND grupo_id = ?")
                .setParameter(1, fonte.id())
                .setParameter(2, grupoId)
                .getSingleResult();
        return Boolean.TRUE.equals(ativa);
    }

    private static String autor(JsonNode data, JsonNode msg) {
        JsonNode meta = msg.path("metadata");
        for (String c : new String[]{texto(meta, "phone"), texto(meta, "sender_id"),
                texto(msg, "senderId"), texto(data, "contactId")}) {
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    private static String textoMensagem(JsonNode msg) {
        String t = texto(msg.path("content"), "text");
        if (t != null) {
            return t;
        }
        return texto(msg.path("content").path("media"), "url");
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n.path(campo);
        return v.isTextual() && !v.asText().isBlank() ? v.asText() : null;
    }

    private static String upper(String s) {
        return s == null ? null : s.toUpperCase();
    }
}
