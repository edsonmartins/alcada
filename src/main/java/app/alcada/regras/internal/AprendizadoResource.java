package app.alcada.regras.internal;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.regras.port.Aprendizado;
import app.alcada.regras.port.Resposta;
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
 * Laço de aprendizado (ADR-0019): gera/lista perguntas situadas e registra a
 * resposta. Escopado por organização (INV-15). Nada é criado sem resposta humana.
 */
@Path("/v1/aprendizado/perguntas")
@Produces(MediaType.APPLICATION_JSON)
public class AprendizadoResource {

    private final Aprendizado aprendizado;
    private final ContextoTenant contexto;

    public AprendizadoResource(Aprendizado aprendizado, ContextoTenant contexto) {
        this.aprendizado = aprendizado;
        this.contexto = contexto;
    }

    @GET
    @Transactional
    public Response listar() {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        return Response.ok(aprendizado.perguntasAbertas(org.get())).build();
    }

    @POST
    @Path("/{id}/responder")
    @Transactional
    public Response responder(@PathParam("id") String id,
                             @HeaderParam("X-Pessoa-Id") String pessoa,
                             RespostaReq req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (req == null || req.resposta() == null) {
            return problema(400, "resposta.ausente", "resposta é obrigatória");
        }
        Resposta r;
        try {
            r = Resposta.valueOf(req.resposta());
        } catch (IllegalArgumentException e) {
            return problema(422, "resposta.invalida", "resposta deve ser SIM, AGORA_NAO ou NAO_PERGUNTAR");
        }
        UUID por = pessoa == null ? null : UUID.fromString(pessoa);
        try {
            aprendizado.responder(org.get(), UUID.fromString(id), r, por);
        } catch (NoSuchElementException e) {
            return problema(404, "pergunta.inexistente", e.getMessage());
        } catch (IllegalStateException e) {
            return problema(409, "pergunta.estado_invalido", e.getMessage());
        } catch (IllegalArgumentException e) {
            return problema(422, "regra.invalida", e.getMessage());
        }
        return Response.noContent().build();
    }

    private static Response problema(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new RegrasResource.Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record RespostaReq(String resposta) {
    }
}
