package app.alcada.autonomia.internal;

import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.ContextoPessoa;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Destinos reconhecíveis para o seletor de repasse (029). */
@Path("/v1/destinos-repasse")
@Produces(MediaType.APPLICATION_JSON)
public class DestinosRepasseResource {
    private final DestinosRepasse destinos;
    private final ContextoTenant contexto;
    private final ContextoPessoa pessoa;

    public DestinosRepasseResource(DestinosRepasse destinos, ContextoTenant contexto, ContextoPessoa pessoa) {
        this.destinos = destinos;
        this.contexto = contexto;
        this.pessoa = pessoa;
    }

    @GET
    public Response buscar(@QueryParam("busca") String busca, @QueryParam("classe") String classe,
                           @QueryParam("limite") Integer limite) {
        Optional<OrgId> org = contexto.atual();
        Optional<UUID> gestor = pessoa.atual();
        if (org.isEmpty() || gestor.isEmpty()) {
            return Response.status(400).type("application/problem+json")
                    .entity(new Problema("urn:alcada:requisicao.invalida", "org e pessoa são obrigatórios", 400))
                    .build();
        }
        return Response.ok(destinos.buscar(org.get(), gestor.get(), busca, classe,
                limite == null ? 8 : limite)).build();
    }

    public record Problema(String type, String detail, int status) {}
}
