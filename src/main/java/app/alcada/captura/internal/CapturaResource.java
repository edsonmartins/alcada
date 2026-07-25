package app.alcada.captura.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import app.alcada.captura.port.MensagemRecebida;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Superfície de captura (docs/API.md). O webhook de eventos autentica-se pela
 * fonte (id + segredo); o cadastro de fontes usa o tenant do contexto.
 */
@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
public class CapturaResource {

    private final Ingestao ingestao;
    private final ContextoTenant contexto;
    private final EntityManager em;

    public CapturaResource(Ingestao ingestao, ContextoTenant contexto, EntityManager em) {
        this.ingestao = ingestao;
        this.contexto = contexto;
        this.em = em;
    }

    // ---- Ingestão (webhook autenticado por fonte) --------------------------

    @POST
    @Path("/captura/eventos")
    public Response ingerir(@HeaderParam("X-Fonte-Segredo") String segredo, MensagemRecebida m) {
        if (m == null || m.fonteId() == null || m.mensagemId() == null) {
            return problema(400, "captura.envelope_invalido", "fonteId e mensagemId são obrigatórios");
        }
        Ingestao.FonteResolvida fonte = ingestao.autenticarFonte(m.fonteId(), segredo);
        if (fonte == null) {
            return problema(401, "fonte.nao_autenticada", "fonte ou segredo inválidos");
        }
        if (!fonte.ativa()) {
            return problema(403, "fonte.inativa", "fonte desativada não capta");
        }
        UUID id = ingestao.ingerir(fonte.org(), m);
        String status = id == null ? "DUPLICADO" : "ACEITO";
        return Response.status(202).entity(new ResultadoIngestao(status, id == null ? null : id.toString())).build();
    }

    // ---- Cadastro de fontes (tenant do contexto) ---------------------------

    @POST
    @Path("/fontes")
    @Transactional
    public Response criarFonte(NovaFonte nova) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        UUID id = UUID.randomUUID();
        String segredo = UUID.randomUUID().toString().replace("-", "");
        em.createNativeQuery("""
                INSERT INTO fonte (id, org_id, tipo, identificador, finalidade, responsavel_id, segredo)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)
                .setParameter(1, id)
                .setParameter(2, org.get().valor())
                .setParameter(3, nova.tipo())
                .setParameter(4, nova.identificador())
                .setParameter(5, nova.finalidade())
                .setParameter(6, nova.responsavelId() == null ? null : UUID.fromString(nova.responsavelId()))
                .setParameter(7, segredo)
                .executeUpdate();
        return Response.status(201).entity(new FonteCriada(id.toString(), segredo)).build();
    }

    @GET
    @Path("/fontes")
    @Transactional
    public Response listarFontes() {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(
                "SELECT id, tipo, identificador, ativa FROM fonte WHERE org_id = ? ORDER BY criada_em")
                .setParameter(1, org.get().valor())
                .getResultList();
        List<FonteResumo> fontes = new ArrayList<>(linhas.size());
        for (Object[] l : linhas) {
            fontes.add(new FonteResumo(l[0].toString(), (String) l[1], (String) l[2], (Boolean) l[3]));
        }
        return Response.ok(fontes).build();
    }

    @POST
    @Path("/fontes/{id}/desativar")
    @Transactional
    public Response desativarFonte(@PathParam("id") String id) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        int n = em.createNativeQuery("UPDATE fonte SET ativa = false WHERE org_id = ? AND id = ?")
                .setParameter(1, org.get().valor())
                .setParameter(2, UUID.fromString(id))
                .executeUpdate();
        return n == 0 ? problema(404, "fonte.inexistente", "fonte não encontrada") : Response.noContent().build();
    }

    private static Response problema(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    // ---- DTOs --------------------------------------------------------------
    public record ResultadoIngestao(String status, String eventoBrutoId) {
    }

    public record NovaFonte(String tipo, String identificador, String finalidade, String responsavelId) {
    }

    public record FonteCriada(String id, String segredo) {
    }

    public record FonteResumo(String id, String tipo, String identificador, boolean ativa) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
