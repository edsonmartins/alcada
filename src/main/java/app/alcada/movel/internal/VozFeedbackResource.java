package app.alcada.movel.internal;

import java.util.Optional;

import app.alcada.movel.port.VozFeedback;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Feedback de confirmação da voz (022). {@code POST /feedback} registra um desfecho
 * (CONFIRMADO/CORRIGIDO); {@code GET /taxa-correcao} devolve a taxa de correção da
 * org — sinal de qualidade da interpretação. Agregado por org (ADR-0017). INV-15.
 */
@Path("/v1/voz")
@Produces(MediaType.APPLICATION_JSON)
public class VozFeedbackResource {

    private final VozFeedback feedback;
    private final ContextoTenant contexto;

    public VozFeedbackResource(VozFeedback feedback, ContextoTenant contexto) {
        this.feedback = feedback;
        this.contexto = contexto;
    }

    @POST
    @Path("/feedback")
    public Response registrar(Req req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (req == null || req.resultado() == null) {
            return problema(400, "requisicao.invalida", "resultado é obrigatório");
        }
        String r = req.resultado().trim().toUpperCase();
        if (!r.equals("CONFIRMADO") && !r.equals("CORRIGIDO")) {
            return problema(400, "resultado.invalido", "resultado deve ser CONFIRMADO ou CORRIGIDO");
        }
        feedback.registrar(org.get(), r.equals("CONFIRMADO"));
        return Response.noContent().build();
    }

    @GET
    @Path("/taxa-correcao")
    public Response taxa(@QueryParam("dias") @DefaultValue("30") int dias) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        VozFeedback.TaxaCorrecao t = feedback.taxa(org.get(), dias);
        return Response.ok(new Resp(t.confirmados(), t.corrigidos(), t.taxa())).build();
    }

    private static Response problema(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record Req(String resultado) {
    }

    public record Resp(long confirmados, long corrigidos, double taxa) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
