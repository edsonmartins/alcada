package app.alcada.metricas.internal;

import java.util.Optional;

import app.alcada.metricas.port.RevisaoSemanal;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Roteiro conduzido da revisão de sexta. Leitura pura, escopada por organização. */
@Path("/v1/revisao-semanal")
@Produces(MediaType.APPLICATION_JSON)
public class RevisaoResource {

    private final RevisaoSemanal revisao;
    private final ContextoTenant contexto;

    public RevisaoResource(RevisaoSemanal revisao, ContextoTenant contexto) {
        this.revisao = revisao;
        this.contexto = contexto;
    }

    @GET
    @Transactional
    public Response revisao() {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return Response.status(400).type("application/problem+json")
                    .entity(new RadarResource.Problema("urn:alcada:org.ausente", "X-Org-Id não resolvido", 400))
                    .build();
        }
        return Response.ok(revisao.calcular(org.get())).build();
    }
}
