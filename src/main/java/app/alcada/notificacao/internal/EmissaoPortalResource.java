package app.alcada.notificacao.internal;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Emissão interna de link de portal (ADR-0013): ação autenticada do tenant. */
@Path("/v1/pendencias")
@Produces(MediaType.APPLICATION_JSON)
public class EmissaoPortalResource {

    @ConfigProperty(name = "portal.base-url", defaultValue = "https://alcada.app/p/")
    String baseUrl;

    private final PortalTokens tokens;
    private final ContextoTenant contexto;
    private final EntityManager em;

    public EmissaoPortalResource(PortalTokens tokens, ContextoTenant contexto, EntityManager em) {
        this.tokens = tokens;
        this.contexto = contexto;
        this.em = em;
    }

    @POST
    @Path("/{id}/portal")
    @Transactional
    public Response emitir(@PathParam("id") String id, EmitirRequest req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (req == null || req.expiraEm() == null) {
            return problema(400, "portal.invalido", "expira_em é obrigatório");
        }
        UUID pendenciaId = UUID.fromString(id);
        long existe = ((Number) em.createNativeQuery(
                "SELECT count(*) FROM pendencia WHERE org_id = ? AND id = ?")
                .setParameter(1, org.get().valor()).setParameter(2, pendenciaId).getSingleResult()).longValue();
        if (existe == 0) {
            return problema(404, "pendencia.inexistente", "pendência não encontrada");
        }
        PortalTokens.TokenEmitido t = tokens.emitir(
                org.get(), pendenciaId, req.oQueFalta(), OffsetDateTime.parse(req.expiraEm()));
        return Response.status(201).entity(new LinkResposta(t.tokenId().toString(), baseUrl + t.token())).build();
    }

    private static Response problema(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record EmitirRequest(String oQueFalta, String expiraEm) {
    }

    public record LinkResposta(String tokenId, String link) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
