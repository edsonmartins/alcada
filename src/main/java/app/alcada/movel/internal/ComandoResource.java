package app.alcada.movel.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import app.alcada.movel.port.Comando;
import app.alcada.movel.port.ComandoMovel;
import app.alcada.movel.port.ResultadoComando;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code POST /v1/comandos} — sincronização do canal móvel (021). Lote idempotente
 * de comandos já estruturados. Escopo por organização (INV-15).
 */
@Path("/v1/comandos")
@Produces(MediaType.APPLICATION_JSON)
public class ComandoResource {

    private final ComandoMovel comandos;
    private final ContextoTenant contexto;

    public ComandoResource(ComandoMovel comandos, ContextoTenant contexto) {
        this.comandos = comandos;
        this.contexto = contexto;
    }

    @POST
    public Response sincronizar(@HeaderParam("X-Pessoa-Id") String pessoa, Lote lote) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty() || pessoa == null) {
            return erro(400, "requisicao.invalida", "X-Org-Id e X-Pessoa-Id são obrigatórios");
        }
        if (lote == null || lote.comandos() == null || lote.comandos().isEmpty()) {
            return erro(400, "lote.vazio", "comandos é obrigatório");
        }
        List<ResultadoComando> r = comandos.sincronizar(org.get(), UUID.fromString(pessoa), lote.comandos());
        return Response.ok(new Resposta(r)).build();
    }

    private static Response erro(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record Lote(List<Comando> comandos) {
    }

    public record Resposta(List<ResultadoComando> resultados) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
