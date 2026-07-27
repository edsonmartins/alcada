package app.alcada.movel.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import app.alcada.identidade.port.Pessoas;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code GET /v1/pessoas} (022) — a equipe da org (exceto o próprio gestor), para
 * o app oferecer um seletor por nome no repasse (em vez de digitar pessoa_id).
 * Só leitura, escopo por organização (INV-15).
 */
@Path("/v1/pessoas")
@Produces(MediaType.APPLICATION_JSON)
public class PessoasResource {

    private final Pessoas pessoas;
    private final ContextoTenant contexto;

    public PessoasResource(Pessoas pessoas, ContextoTenant contexto) {
        this.pessoas = pessoas;
        this.contexto = contexto;
    }

    @GET
    public Response listar(@HeaderParam("X-Pessoa-Id") String pessoa) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty() || pessoa == null) {
            return Response.status(400).type("application/problem+json")
                    .entity(new Problema("urn:alcada:requisicao.invalida",
                            "X-Org-Id e X-Pessoa-Id são obrigatórios", 400)).build();
        }
        List<Pessoa> lista = pessoas.listar(org.get(), UUID.fromString(pessoa)).stream()
                .map(p -> new Pessoa(p.id().toString(), p.nome())).toList();
        return Response.ok(lista).build();
    }

    public record Pessoa(String id, String nome) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
