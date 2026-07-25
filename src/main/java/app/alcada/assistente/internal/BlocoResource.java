package app.alcada.assistente.internal;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import app.alcada.assistente.port.Bloco;
import app.alcada.assistente.port.Dossie;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Bloco de decisão de uma pendência (RFC-0004). Montar e redigir são proposta;
 * decidir é a ação do gestor (INV-10). Escopo por organização (INV-15).
 */
@Path("/v1/pendencias/{id}")
@Produces(MediaType.APPLICATION_JSON)
public class BlocoResource {

    private final Bloco bloco;
    private final Dossie dossie;
    private final ContextoTenant contexto;

    public BlocoResource(Bloco bloco, Dossie dossie, ContextoTenant contexto) {
        this.bloco = bloco;
        this.dossie = dossie;
        this.contexto = contexto;
    }

    @GET
    @Path("/bloco")
    @Transactional
    public Response montar(@PathParam("id") String id) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return erro(400, "org.ausente", "X-Org-Id não resolvido");
        }
        try {
            return Response.ok(bloco.montar(org.get(), UUID.fromString(id))).build();
        } catch (NoSuchElementException e) {
            return erro(404, "pendencia.inexistente", e.getMessage());
        }
    }

    @POST
    @Path("/bloco/redigir")
    @Transactional
    public Response redigir(@PathParam("id") String id, RedigirReq req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return erro(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (req == null || req.opcao() == null) {
            return erro(400, "redigir.invalido", "opcao é obrigatória");
        }
        String tom = req.tom() == null ? "direto" : req.tom();
        try {
            return Response.ok(bloco.redigir(org.get(), UUID.fromString(id), req.opcao(), tom)).build();
        } catch (NoSuchElementException e) {
            return erro(404, "pendencia.inexistente", e.getMessage());
        }
    }

    @POST
    @Path("/decidir")
    @Transactional
    public Response decidir(@PathParam("id") String id, @HeaderParam("X-Pessoa-Id") String pessoa, DecidirReq req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return erro(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (req == null || req.opcao() == null) {
            return erro(400, "decidir.invalido", "opcao é obrigatória");
        }
        UUID por = pessoa == null ? null : UUID.fromString(pessoa);
        try {
            bloco.decidir(org.get(), UUID.fromString(id), req.opcao(), req.texto(), por);
            return Response.noContent().build();
        } catch (NoSuchElementException e) {
            return erro(404, "pendencia.inexistente", e.getMessage());
        } catch (IllegalStateException e) {
            return erro(409, "pendencia.estado_invalido", e.getMessage());
        }
    }

    @POST
    @Path("/dossie/perguntar")
    @Transactional
    public Response perguntar(@PathParam("id") String id, PerguntarReq req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return erro(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (req == null || req.pergunta() == null || req.pergunta().isBlank()) {
            return erro(400, "pergunta.ausente", "pergunta é obrigatória");
        }
        try {
            return Response.ok(dossie.perguntar(org.get(), UUID.fromString(id), req.pergunta())).build();
        } catch (NoSuchElementException e) {
            return erro(404, "pendencia.inexistente", e.getMessage());
        }
    }

    private static Response erro(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record RedigirReq(String opcao, String tom) {
    }

    public record DecidirReq(String opcao, String texto) {
    }

    public record PerguntarReq(String pergunta) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
