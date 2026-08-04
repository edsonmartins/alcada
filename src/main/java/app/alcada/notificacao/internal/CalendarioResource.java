package app.alcada.notificacao.internal;

import java.util.Optional;
import java.util.UUID;

import app.alcada.notificacao.port.ContasCalendario;
import app.alcada.notificacao.port.OauthCalendario;
import app.alcada.plataforma.multitenancy.port.ContextoPessoa;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Calendário do gestor (RFC-0009 F2.3b): conectar, ver e desconectar. A conta é
 * pessoal — quem conecta é quem está no contexto, nunca "o tenant". Os tokens
 * ficam cifrados e nunca voltam por aqui.
 */
@Path("/v1/calendario")
@Produces(MediaType.APPLICATION_JSON)
public class CalendarioResource {

    private final ContasCalendario contas;
    private final OauthCalendario oauth;
    private final ContextoTenant contexto;
    private final ContextoPessoa contextoPessoa;

    public CalendarioResource(ContasCalendario contas, OauthCalendario oauth,
                              ContextoTenant contexto, ContextoPessoa contextoPessoa) {
        this.contas = contas;
        this.oauth = oauth;
        this.contexto = contexto;
        this.contextoPessoa = contextoPessoa;
    }

    @GET
    public Response estado() {
        Optional<OrgId> org = contexto.atual();
        Optional<UUID> pessoa = contextoPessoa.atual();
        if (org.isEmpty() || pessoa.isEmpty()) {
            return problema(400, "requisicao.invalida", "X-Org-Id e X-Pessoa-Id são obrigatórios");
        }
        return contas.doGestor(org.get(), pessoa.get())
                .map(c -> Response.ok(new EstadoConta(true, c.provedor(), c.escopo())).build())
                .orElseGet(() -> Response.ok(new EstadoConta(false, null, null)).build());
    }

    @POST
    public Response conectar(ConectarRequest req) {
        Optional<OrgId> org = contexto.atual();
        Optional<UUID> pessoa = contextoPessoa.atual();
        if (org.isEmpty() || pessoa.isEmpty()) {
            return problema(400, "requisicao.invalida", "X-Org-Id e X-Pessoa-Id são obrigatórios");
        }
        if (req == null || req.codigo() == null || req.codigo().isBlank()) {
            return problema(400, "calendario.sem_codigo", "codigo do consentimento é obrigatório");
        }
        try {
            var conta = oauth.trocar(req.codigo(), req.redirectUri());
            contas.salvar(org.get(), pessoa.get(), conta);
            return Response.ok(new EstadoConta(true, conta.provedor(), conta.escopo())).build();
        } catch (OauthCalendario.ConsentimentoInvalido e) {
            return problema(422, "calendario.consentimento_invalido", e.getMessage());
        }
    }

    @DELETE
    public Response revogar() {
        Optional<OrgId> org = contexto.atual();
        Optional<UUID> pessoa = contextoPessoa.atual();
        if (org.isEmpty() || pessoa.isEmpty()) {
            return problema(400, "requisicao.invalida", "X-Org-Id e X-Pessoa-Id são obrigatórios");
        }
        contas.revogar(org.get(), pessoa.get());
        return Response.noContent().build();
    }

    private static Response problema(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record ConectarRequest(String codigo, String redirectUri) {}

    /** Nunca devolve token: só se há conta, de qual provedor e com que escopo. */
    public record EstadoConta(boolean conectado, String provedor, String escopo) {}

    public record Problema(String type, String detail, int status) {}
}
