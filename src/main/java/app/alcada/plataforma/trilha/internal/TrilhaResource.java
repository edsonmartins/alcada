package app.alcada.plataforma.trilha.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoRegistrado;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Consulta da trilha de uma pendência (docs/API.md). Sempre escopada pela
 * organização do contexto (INV-15). Erros em {@code application/problem+json}
 * (RFC 7807).
 */
@Path("/v1/pendencias/{id}/trilha")
public class TrilhaResource {

    private final ConsultaTrilha consulta;
    private final ContextoTenant contexto;

    public TrilhaResource(ConsultaTrilha consulta, ContextoTenant contexto) {
        this.consulta = consulta;
        this.contexto = contexto;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response daPendencia(@PathParam("id") String id) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problem(Response.Status.BAD_REQUEST, "org.ausente",
                    "X-Org-Id não resolvido no contexto da requisição");
        }
        UUID pendenciaId;
        try {
            pendenciaId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return problem(Response.Status.BAD_REQUEST, "pendencia.id_invalido",
                    "id de pendência não é um UUID");
        }
        List<EventoRegistrado> eventos = consulta.daPendencia(org.get(), pendenciaId);
        return Response.ok(eventos).build();
    }

    private static Response problem(Response.Status status, String tipo, String detalhe) {
        return Response.status(status)
                .type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, status.getReasonPhrase(),
                        status.getStatusCode(), detalhe))
                .build();
    }

    /** Corpo RFC 7807. */
    public record Problema(String type, String title, int status, String detail) {
    }
}
