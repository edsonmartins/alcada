package app.alcada.notificacao.internal;

import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Revogação interna de token de portal (ADR-0013). */
@Path("/v1/portal")
@Produces(MediaType.APPLICATION_JSON)
public class RevogacaoPortalResource {

    private final PortalTokens tokens;
    private final ContextoTenant contexto;

    public RevogacaoPortalResource(PortalTokens tokens, ContextoTenant contexto) {
        this.tokens = tokens;
        this.contexto = contexto;
    }

    @POST
    @Path("/{tokenId}/revogar")
    @Transactional
    public Response revogar(@PathParam("tokenId") String tokenId) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return Response.status(400).type("application/problem+json")
                    .entity(new EmissaoPortalResource.Problema("urn:alcada:org.ausente", "X-Org-Id não resolvido", 400))
                    .build();
        }
        boolean ok = tokens.revogar(org.get(), UUID.fromString(tokenId));
        return ok ? Response.noContent().build() : Response.status(404).build();
    }
}
