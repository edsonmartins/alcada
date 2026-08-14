package app.alcada.notificacao.internal;

import java.net.URI;
import java.util.UUID;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

/** Fallback web quando o aplicativo não está instalado. */
@Path("/app/delegacoes")
public class LinkAplicativoResource {
    @GET @Path("/{id}")
    public Response abrir(@PathParam("id") String id) {
        try {
            UUID.fromString(id);
            return Response.seeOther(URI.create("/executor?delegacaoId=" + id)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(404).build();
        }
    }
}
