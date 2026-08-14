package app.alcada.identidade.internal;

import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.ContextoPessoa;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Canais declarados pela própria pessoa; nunca aparecem no diretório da equipe. */
@Path("/v1/perfil/canais")
@Produces(MediaType.APPLICATION_JSON)
public class CanaisPessoaResource {
    private final EntityManager em;
    private final ContextoTenant contexto;
    private final ContextoPessoa contextoPessoa;

    public CanaisPessoaResource(EntityManager em, ContextoTenant contexto, ContextoPessoa contextoPessoa) {
        this.em = em;
        this.contexto = contexto;
        this.contextoPessoa = contextoPessoa;
    }

    @GET
    @Transactional
    public Response obter() {
        Escopo e = escopo();
        if (e == null) return erro(400, "requisicao.invalida", "org e pessoa são obrigatórios");
        var rs = em.createNativeQuery("SELECT whatsapp FROM pessoa WHERE org_id=? AND id=?")
                .setParameter(1, e.org.valor()).setParameter(2, e.pessoa).getResultList();
        if (rs.isEmpty()) return erro(404, "pessoa.inexistente", "pessoa não encontrada");
        return Response.ok(new Canais((String) rs.getFirst())).build();
    }

    @PUT
    @Transactional
    public Response salvar(Canais req) {
        Escopo e = escopo();
        if (e == null) return erro(400, "requisicao.invalida", "org e pessoa são obrigatórios");
        String whatsapp = req == null ? null : limpar(req.whatsapp());
        if (whatsapp != null && !whatsapp.matches("^\\+[1-9]\\d{9,14}$")) {
            return erro(422, "whatsapp.invalido", "use o formato internacional, por exemplo +5544999990000");
        }
        try {
            int n = em.createNativeQuery("UPDATE pessoa SET whatsapp=? WHERE org_id=? AND id=?")
                    .setParameter(1, whatsapp).setParameter(2, e.org.valor()).setParameter(3, e.pessoa)
                    .executeUpdate();
            return n == 0 ? erro(404, "pessoa.inexistente", "pessoa não encontrada")
                    : Response.noContent().build();
        } catch (org.hibernate.exception.ConstraintViolationException ex) {
            return erro(409, "whatsapp.em_uso", "este WhatsApp já pertence a outra pessoa da organização");
        }
    }

    private Escopo escopo() {
        Optional<OrgId> org = contexto.atual();
        Optional<UUID> pessoa = contextoPessoa.atual();
        return org.isEmpty() || pessoa.isEmpty() ? null : new Escopo(org.get(), pessoa.get());
    }

    private static String limpar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static Response erro(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record Canais(String whatsapp) {}
    public record Problema(String type, String detail, int status) {}
    private record Escopo(OrgId org, UUID pessoa) {}
}
