package app.alcada.autonomia.internal;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Ações do motor sobre a pendência (docs/API.md): delegar, intervir, desfazer.
 * Erros em {@code application/problem+json}.
 */
@Path("/v1/pendencias/{id}")
@Produces(MediaType.APPLICATION_JSON)
public class AutonomiaResource {

    private final MotorAutonomia motor;
    private final ContextoTenant contexto;

    public AutonomiaResource(MotorAutonomia motor, ContextoTenant contexto) {
        this.motor = motor;
        this.contexto = contexto;
    }

    @POST
    @Path("/delegar")
    public Response delegar(@PathParam("id") String id, @HeaderParam("X-Pessoa-Id") String pessoa,
                            DelegarRequest req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (pessoa == null) {
            return problema(400, "pessoa.ausente", "X-Pessoa-Id é obrigatório");
        }
        if (req == null || req.donoId() == null || req.nivel() == null || req.prazo() == null) {
            return problema(400, "delegar.invalido", "donoId, nivel e prazo são obrigatórios");
        }
        try {
            UUID delegacao = motor.delegar(org.get(), UUID.fromString(id), UUID.fromString(req.donoId()),
                    req.nivel(), OffsetDateTime.parse(req.prazo()), UUID.fromString(pessoa));
            return Response.status(201).entity(new DelegacaoCriada(delegacao.toString())).build();
        } catch (Falhas.Inelegivel e) {
            return problema(422, "alcada.inelegivel", e.getMessage());
        } catch (Falhas.EstadoInvalido e) {
            return problema(409, "pendencia.estado_invalido", e.getMessage());
        }
    }

    @POST
    @Path("/intervir")
    public Response intervir(@PathParam("id") String id, @HeaderParam("X-Pessoa-Id") String pessoa) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty() || pessoa == null) {
            return problema(400, "requisicao.invalida", "X-Org-Id e X-Pessoa-Id são obrigatórios");
        }
        try {
            motor.intervir(org.get(), UUID.fromString(id), UUID.fromString(pessoa));
            return Response.noContent().build();
        } catch (Falhas.EstadoInvalido e) {
            return problema(409, "pendencia.estado_invalido", e.getMessage());
        }
    }

    @POST
    @Path("/desfazer")
    public Response desfazer(@PathParam("id") String id, @HeaderParam("X-Pessoa-Id") String pessoa) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty() || pessoa == null) {
            return problema(400, "requisicao.invalida", "X-Org-Id e X-Pessoa-Id são obrigatórios");
        }
        try {
            motor.desfazer(org.get(), UUID.fromString(id), UUID.fromString(pessoa));
            return Response.noContent().build();
        } catch (Falhas.JanelaExpirada e) {
            return problema(409, "janela.expirada", e.getMessage());
        } catch (Falhas.EstadoInvalido e) {
            return problema(409, "pendencia.estado_invalido", e.getMessage());
        }
    }

    private static Response problema(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record DelegarRequest(String donoId, String nivel, String prazo) {
    }

    public record DelegacaoCriada(String delegacaoId) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
