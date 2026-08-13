package app.alcada.autonomia.internal;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.ContextoPessoa;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.autonomia.port.ContatosExternos;
import app.alcada.autonomia.port.DestinoRepasse;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.transaction.Transactional;
import jakarta.persistence.EntityManager;

/**
 * Ações do motor sobre a pendência (docs/API.md): delegar, intervir, desfazer.
 * Erros em {@code application/problem+json}.
 */
@Path("/v1/pendencias/{id}")
@Produces(MediaType.APPLICATION_JSON)
public class AutonomiaResource {

    private final MotorAutonomia motor;
    private final ContextoTenant contexto;
    private final ContextoPessoa contextoPessoa;
    private final ContatosExternos contatos;
    private final EntityManager em;

    public AutonomiaResource(MotorAutonomia motor, ContextoTenant contexto, ContextoPessoa contextoPessoa,
                             ContatosExternos contatos, EntityManager em) {
        this.motor = motor;
        this.contexto = contexto;
        this.contextoPessoa = contextoPessoa;
        this.contatos = contatos;
        this.em = em;
    }

    @POST
    @Path("/delegar")
    public Response delegar(@PathParam("id") String id, DelegarRequest req) {
        Optional<OrgId> org = contexto.atual();
        Optional<UUID> pessoa = contextoPessoa.atual();
        if (org.isEmpty() || pessoa.isEmpty()) return problema(400, "requisicao.invalida", "org e pessoa são obrigatórios");
        if (req == null || req.donoId() == null || req.nivel() == null || req.prazo() == null)
            return problema(400, "delegar.invalido", "donoId, nível e prazo são obrigatórios");
        try {
            UUID delegacao = motor.delegar(org.get(), UUID.fromString(id), UUID.fromString(req.donoId()),
                    req.nivel(), OffsetDateTime.parse(req.prazo()), pessoa.get());
            return Response.status(201).entity(new DelegacaoCriada(delegacao.toString())).build();
        } catch (Falhas.Inelegivel e) {
            return problema(422, "alcada.inelegivel", e.getMessage());
        } catch (Falhas.EstadoInvalido e) {
            return problema(409, "pendencia.estado_invalido", e.getMessage());
        } catch (IllegalArgumentException e) {
            return problema(422, "delegar.invalido", e.getMessage());
        }
    }

    @POST
    @Path("/repassar")
    @Transactional
    public Response repassar(@PathParam("id") String id, DelegarRequest req) {
        Optional<OrgId> org = contexto.atual();
        Optional<UUID> pessoa = contextoPessoa.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (pessoa.isEmpty()) {
            return problema(400, "pessoa.ausente", "X-Pessoa-Id é obrigatório");
        }
        if (req == null || req.nivel() == null || req.prazo() == null) {
            return problema(400, "delegar.invalido", "destino, nível e prazo são obrigatórios");
        }
        try {
            DestinoRepasse destino = resolverDestino(org.get(), pessoa.get(), req);
            UUID delegacao = motor.delegar(org.get(), UUID.fromString(id), destino, req.nivel(),
                    OffsetDateTime.parse(req.prazo()), pessoa.get());
            return Response.status(201).entity(new DelegacaoCriada(delegacao.toString())).build();
        } catch (IllegalArgumentException e) {
            return problema(422, "delegar.invalido", e.getMessage());
        } catch (Falhas.Inelegivel e) {
            return problema(422, "alcada.inelegivel", e.getMessage());
        } catch (Falhas.EstadoInvalido e) {
            return problema(409, "pendencia.estado_invalido", e.getMessage());
        }
    }

    private DestinoRepasse resolverDestino(OrgId org, UUID gestor, DelegarRequest req) {
        if (req.destino() == null) {
            if (req.donoId() == null) throw new IllegalArgumentException("destino é obrigatório");
            return interno(org, req.donoId());
        }
        DestinoRequest d = req.destino();
        return switch (d.tipo()) {
            case "INTERNO" -> interno(org, d.pessoaId());
            case "EXTERNO" -> new DestinoRepasse.Externo(UUID.fromString(d.contatoId()));
            case "EXTERNO_NOVO" -> new DestinoRepasse.Externo(
                    contatos.registrar(org, d.nome(), d.canal(), d.endereco(), gestor));
            default -> throw new IllegalArgumentException("tipo de destino inválido");
        };
    }

    private DestinoRepasse interno(OrgId org, String pessoaId) {
        UUID id = UUID.fromString(pessoaId);
        Number n = (Number) em.createNativeQuery("SELECT count(*) FROM pessoa WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, id).getSingleResult();
        if (n.longValue() != 1) throw new IllegalArgumentException("destino de repasse não encontrado");
        return new DestinoRepasse.Interno(id);
    }

    @POST
    @Path("/intervir")
    public Response intervir(@PathParam("id") String id, IntervirRequest req) {
        Optional<OrgId> org = contexto.atual();
        Optional<UUID> pessoa = contextoPessoa.atual();
        if (org.isEmpty() || pessoa.isEmpty()) {
            return problema(400, "requisicao.invalida", "X-Org-Id e X-Pessoa-Id são obrigatórios");
        }
        try {
            motor.intervir(org.get(), UUID.fromString(id), pessoa.get(), req == null ? null : req.motivo(),
                    req == null ? null : req.observacao());
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return problema(422, "intervencao.invalida", e.getMessage());
        } catch (Falhas.EstadoInvalido e) {
            return problema(409, "pendencia.estado_invalido", e.getMessage());
        }
    }

    @POST
    @Path("/desfazer")
    public Response desfazer(@PathParam("id") String id) {
        Optional<OrgId> org = contexto.atual();
        Optional<UUID> pessoa = contextoPessoa.atual();
        if (org.isEmpty() || pessoa.isEmpty()) {
            return problema(400, "requisicao.invalida", "X-Org-Id e X-Pessoa-Id são obrigatórios");
        }
        try {
            motor.desfazer(org.get(), UUID.fromString(id), pessoa.get());
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

    public record DelegarRequest(String donoId, DestinoRequest destino, String nivel, String prazo) {
    }

    public record DestinoRequest(String tipo, String pessoaId, String contatoId, String nome,
                                 String canal, String endereco) {}
    public record IntervirRequest(String motivo, String observacao) {}

    public record DelegacaoCriada(String delegacaoId) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
