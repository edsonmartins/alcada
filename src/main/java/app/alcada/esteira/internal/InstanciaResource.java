package app.alcada.esteira.internal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import app.alcada.esteira.port.Avaliacoes;
import app.alcada.esteira.port.EntradasEsteira.ApontamentoItem;
import app.alcada.esteira.port.EntradasEsteira.ResultadoItem;
import app.alcada.esteira.port.Esteiras;
import app.alcada.esteira.port.PortalInstancia;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Avaliar e avançar uma instância (regra de avanço, RFC-0006). */
@Path("/v1/instancias")
@Produces(MediaType.APPLICATION_JSON)
public class InstanciaResource {

    private static final int VALIDADE_PADRAO_DIAS = 30;

    private final Avaliacoes avaliacoes;
    private final Esteiras esteiras;
    private final PortalInstancia portal;
    private final ContextoTenant contexto;

    public InstanciaResource(Avaliacoes avaliacoes, Esteiras esteiras, PortalInstancia portal,
                             ContextoTenant contexto) {
        this.avaliacoes = avaliacoes;
        this.esteiras = esteiras;
        this.portal = portal;
        this.contexto = contexto;
    }

    @POST
    @Path("/{id}/portal")
    @Transactional
    public Response emitirPortal(@PathParam("id") String id) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return erro(400, "org.ausente", "X-Org-Id não resolvido");
        }
        OffsetDateTime expira = OffsetDateTime.now(ZoneOffset.UTC).plusDays(VALIDADE_PADRAO_DIAS);
        var t = portal.emitir(org.get(), UUID.fromString(id), expira);
        return Response.status(201).entity(t).build();
    }

    @POST
    @Path("/portais/{tokenId}/revogar")
    @Transactional
    public Response revogarPortal(@PathParam("tokenId") String tokenId) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return erro(400, "org.ausente", "X-Org-Id não resolvido");
        }
        portal.revogar(org.get(), UUID.fromString(tokenId));
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/avaliar")
    @Transactional
    public Response avaliar(@PathParam("id") String id, @HeaderParam("X-Pessoa-Id") String pessoa, AvaliarReq req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return erro(400, "org.ausente", "X-Org-Id não resolvido");
        }
        List<ResultadoItem> resultados = req == null || req.resultados() == null ? List.of() : req.resultados();
        List<ApontamentoItem> apontamentos = req == null || req.apontamentos() == null ? List.of() : req.apontamentos();
        UUID avaliador = pessoa == null ? null : UUID.fromString(pessoa);
        try {
            var r = avaliacoes.avaliar(org.get(), UUID.fromString(id), resultados, apontamentos, avaliador);
            return Response.ok(r).build();
        } catch (NoSuchElementException e) {
            return erro(404, "instancia.inexistente", e.getMessage());
        } catch (IllegalStateException e) {
            return erro(409, "instancia.estado_invalido", e.getMessage());
        }
    }

    @POST
    @Path("/{id}/avancar")
    @Transactional
    public Response avancar(@PathParam("id") String id) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return erro(400, "org.ausente", "X-Org-Id não resolvido");
        }
        try {
            esteiras.avancar(org.get(), UUID.fromString(id));
            return Response.noContent().build();
        } catch (NoSuchElementException e) {
            return erro(404, "instancia.inexistente", e.getMessage());
        }
    }

    private static Response erro(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new EsteiraResource.Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record AvaliarReq(List<ResultadoItem> resultados, List<ApontamentoItem> apontamentos) {
    }
}
