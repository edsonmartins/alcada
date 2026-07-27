package app.alcada.consulta.internal;

import java.util.Optional;
import java.util.UUID;

import app.alcada.consulta.port.Consulta;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code POST /v1/consulta} — consulta em linguagem natural sobre a fila
 * (RFC-0004 §3). Só leitura, escopo por organização (INV-15).
 */
@Path("/v1/consulta")
@Produces(MediaType.APPLICATION_JSON)
public class ConsultaResource {

    private final Consulta consulta;
    private final ContextoTenant contexto;

    public ConsultaResource(Consulta consulta, ContextoTenant contexto) {
        this.consulta = consulta;
        this.contexto = contexto;
    }

    @POST
    @Transactional
    public Response consultar(@HeaderParam("X-Pessoa-Id") String pessoa, Req req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return erro(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (req == null || req.pergunta() == null || req.pergunta().isBlank()) {
            return erro(400, "pergunta.ausente", "pergunta é obrigatória");
        }
        // gestor (X-Pessoa-Id) é opcional: só "o que eu decidi" o usa; demais são org-escopados.
        UUID gestor = pessoa == null || pessoa.isBlank() ? null : UUID.fromString(pessoa);
        return Response.ok(consulta.consultar(org.get(), gestor, req.pergunta())).build();
    }

    private static Response erro(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record Req(String pergunta) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
