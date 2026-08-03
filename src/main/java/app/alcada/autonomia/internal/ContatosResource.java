package app.alcada.autonomia.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import app.alcada.autonomia.port.ContatosExternos;
import app.alcada.plataforma.multitenancy.port.ContextoPessoa;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Contatos externos de repasse (RFC-0008 F1.4a): criar e listar. Alimenta a
 * escolha de destino no assistente e as telas de config. Erros em problem+json.
 */
@Path("/v1/contatos")
@Produces(MediaType.APPLICATION_JSON)
public class ContatosResource {

    private final ContatosExternos contatos;
    private final ContextoTenant contexto;
    private final ContextoPessoa contextoPessoa;

    public ContatosResource(ContatosExternos contatos, ContextoTenant contexto, ContextoPessoa contextoPessoa) {
        this.contatos = contatos;
        this.contexto = contexto;
        this.contextoPessoa = contextoPessoa;
    }

    @POST
    public Response criar(CriarContato req) {
        Optional<OrgId> org = contexto.atual();
        Optional<UUID> pessoa = contextoPessoa.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (pessoa.isEmpty()) {
            return problema(400, "pessoa.ausente", "X-Pessoa-Id é obrigatório");
        }
        if (req == null || vazio(req.nome()) || vazio(req.canal()) || vazio(req.endereco())) {
            return problema(400, "contato.invalido", "nome, canal e endereco são obrigatórios");
        }
        try {
            UUID id = contatos.registrar(org.get(), req.nome(), req.canal(), req.endereco(), pessoa.get());
            return Response.status(201).entity(new ContatoCriado(id.toString())).build();
        } catch (IllegalArgumentException e) {
            return problema(422, "contato.invalido", e.getMessage());
        }
    }

    @GET
    public Response listar() {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        List<ContatoView> view = contatos.listar(org.get()).stream()
                .map(c -> new ContatoView(c.id().toString(), c.nome(), c.canal(), c.endereco()))
                .toList();
        return Response.ok(view).build();
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    private static Response problema(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record CriarContato(String nome, String canal, String endereco) {}

    public record ContatoCriado(String id) {}

    public record ContatoView(String id, String nome, String canal, String endereco) {}

    public record Problema(String type, String detail, int status) {}
}
