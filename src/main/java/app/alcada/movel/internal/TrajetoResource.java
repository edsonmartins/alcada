package app.alcada.movel.internal;

import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.port.Outbox;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code POST /v1/trajeto/liberar} (023) — libera os efeitos externos represados
 * de um trajeto ao estacionar + confirmar o resumo (INV-14). Só então terceiros
 * são comunicados. Escopo por organização (INV-15).
 */
@Path("/v1/trajeto/liberar")
@Produces(MediaType.APPLICATION_JSON)
public class TrajetoResource {

    private final Outbox outbox;
    private final ContextoTenant contexto;

    public TrajetoResource(Outbox outbox, ContextoTenant contexto) {
        this.outbox = outbox;
        this.contexto = contexto;
    }

    @POST
    @Transactional
    public Response liberar(Req req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (req == null || req.trajetoId() == null || req.trajetoId().isBlank()) {
            return problema(400, "trajeto.ausente", "trajetoId é obrigatório");
        }
        outbox.liberarTrajeto(org.get(), UUID.fromString(req.trajetoId()));
        return Response.noContent().build();
    }

    private static Response problema(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record Req(String trajetoId) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
