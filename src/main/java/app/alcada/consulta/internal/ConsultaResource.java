package app.alcada.consulta.internal;

import java.util.Optional;
import java.util.UUID;

import app.alcada.consulta.port.Consulta;
import app.alcada.plataforma.multitenancy.port.ContextoPessoa;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.transaction.Transactional;
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
    private final ContextoPessoa contextoPessoa;

    public ConsultaResource(Consulta consulta, ContextoTenant contexto, ContextoPessoa contextoPessoa) {
        this.consulta = consulta;
        this.contexto = contexto;
        this.contextoPessoa = contextoPessoa;
    }

    @POST
    @Transactional
    public Response consultar(Req req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return erro(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (req == null || req.pergunta() == null || req.pergunta().isBlank()) {
            return erro(400, "pergunta.ausente", "pergunta é obrigatória");
        }
        // gestor (pessoa) é opcional: só "o que eu decidi" o usa; demais são org-escopados.
        UUID gestor = contextoPessoa.atual().orElse(null);
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
