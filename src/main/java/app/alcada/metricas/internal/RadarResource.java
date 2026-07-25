package app.alcada.metricas.internal;

import java.util.Optional;

import app.alcada.metricas.port.Radar;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Radar de gargalo (ADR-0017: diagnóstico organizacional, nunca placar). Leitura
 * pura, escopada por organização (INV-15).
 */
@Path("/v1/radar")
@Produces(MediaType.APPLICATION_JSON)
public class RadarResource {

    private final Radar radar;
    private final ContextoTenant contexto;

    public RadarResource(Radar radar, ContextoTenant contexto) {
        this.radar = radar;
        this.contexto = contexto;
    }

    @GET
    @Transactional
    public Response radar() {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return Response.status(400).type("application/problem+json")
                    .entity(new Problema("urn:alcada:org.ausente", "X-Org-Id não resolvido", 400)).build();
        }
        return Response.ok(radar.calcular(org.get())).build();
    }

    public record Problema(String type, String detail, int status) {
    }
}
