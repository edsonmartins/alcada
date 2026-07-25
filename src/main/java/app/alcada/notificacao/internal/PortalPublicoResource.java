package app.alcada.notificacao.internal;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Portal público sem login (ADR-0013). O token É a credencial. Projeção
 * allowlist; {@code no-index}; {@code 404} uniforme para inválido/expirado/
 * revogado (a resolução faz sempre uma única consulta — latência uniforme).
 */
@Path("/p")
@Produces(MediaType.APPLICATION_JSON)
public class PortalPublicoResource {

    private final PortalTokens tokens;

    public PortalPublicoResource(PortalTokens tokens) {
        this.tokens = tokens;
    }

    @GET
    @Path("/{token}")
    @Transactional
    public Response ver(@PathParam("token") String token) {
        return tokens.resolver(token)
                .map(p -> semIndice(Response.ok(p)))
                .orElseGet(() -> semIndice(Response.status(404)))
                .build();
    }

    private static Response.ResponseBuilder semIndice(Response.ResponseBuilder rb) {
        return rb.header("X-Robots-Tag", "noindex, nofollow");
    }
}
