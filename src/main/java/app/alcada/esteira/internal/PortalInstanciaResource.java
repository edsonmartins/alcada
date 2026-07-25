package app.alcada.esteira.internal;

import java.util.List;
import java.util.NoSuchElementException;

import app.alcada.esteira.port.PortalInstancia;
import app.alcada.esteira.port.PortalInstancia.Declaracao;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Portal público da instância (ADR-0013). O token É a credencial — sem login,
 * sem contexto de tenant por header (o token resolve o org). Projeção curada;
 * resposta uniforme para inválido/expirado/revogado. {@code noindex}.
 */
@Path("/pi")
@Produces(MediaType.APPLICATION_JSON)
public class PortalInstanciaResource {

    private final PortalInstancia portal;

    public PortalInstanciaResource(PortalInstancia portal) {
        this.portal = portal;
    }

    @GET
    @Path("/{token}")
    @Transactional
    public Response ver(@PathParam("token") String token) {
        return portal.resolver(token)
                .map(e -> Response.ok(e).header("X-Robots-Tag", "noindex, nofollow").build())
                .orElseGet(() -> naoEncontrado());
    }

    @POST
    @Path("/{token}/autoavaliacao")
    @Transactional
    public Response autoavaliar(@PathParam("token") String token, AutoavaliacaoReq req) {
        List<Declaracao> decls = req == null || req.declaracoes() == null ? List.of() : req.declaracoes();
        try {
            portal.autoavaliar(token, decls);
            return Response.noContent().header("X-Robots-Tag", "noindex, nofollow").build();
        } catch (NoSuchElementException e) {
            return naoEncontrado();
        }
    }

    /** Uniforme: inválido/expirado/revogado não se distinguem. */
    private static Response naoEncontrado() {
        return Response.status(404).type("application/problem+json")
                .header("X-Robots-Tag", "noindex, nofollow")
                .entity(new Problema("urn:alcada:portal.nao_encontrado", "link inválido ou expirado", 404))
                .build();
    }

    public record AutoavaliacaoReq(List<Declaracao> declaracoes) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
