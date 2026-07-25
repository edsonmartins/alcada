package app.alcada.triagem.internal;

import java.util.List;
import java.util.Optional;

import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code GET /v1/hoje} — no máximo 3 itens, com justificativa por item
 * (PRODUTO §6). Resource próprio para não colidir com os prefixos de
 * {@code /v1/pendencias/...}.
 */
@Path("/v1/hoje")
@Produces(MediaType.APPLICATION_JSON)
public class HojeResource {

    private final TriagemService triagem;
    private final ContextoTenant contexto;

    public HojeResource(TriagemService triagem, ContextoTenant contexto) {
        this.triagem = triagem;
        this.contexto = contexto;
    }

    @GET
    @Transactional
    public Response hoje() {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return Response.status(400).type("application/problem+json")
                    .entity(new TriagemResource.Problema("urn:alcada:org.ausente", "X-Org-Id não resolvido", 400))
                    .build();
        }
        List<TriagemService.ItemHoje> itens = triagem.hoje(org.get());
        return Response.ok(itens).build();
    }
}
