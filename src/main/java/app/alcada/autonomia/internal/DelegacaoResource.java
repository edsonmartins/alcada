package app.alcada.autonomia.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.ContextoPessoa;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Superfície de delegações (docs/API.md). A UI do executor é o pacote 005; aqui
 * ficam a API de proposta e a listagem.
 */
@Path("/v1/delegacoes")
@Produces(MediaType.APPLICATION_JSON)
public class DelegacaoResource {

    private final MotorAutonomia motor;
    private final ContextoTenant contexto;
    private final ContextoPessoa contextoPessoa;
    private final EntityManager em;

    public DelegacaoResource(MotorAutonomia motor, ContextoTenant contexto, ContextoPessoa contextoPessoa,
                             EntityManager em) {
        this.motor = motor;
        this.contexto = contexto;
        this.contextoPessoa = contextoPessoa;
        this.em = em;
    }

    @POST
    @Path("/{id}/propor")
    public Response propor(@PathParam("id") String id, ProporRequest req) {
        Optional<OrgId> org = contexto.atual();
        Optional<UUID> pessoa = contextoPessoa.atual();
        if (org.isEmpty() || pessoa.isEmpty()) {
            return problema(400, "requisicao.invalida", "X-Org-Id e X-Pessoa-Id são obrigatórios");
        }
        if (req == null || req.proposta() == null) {
            return problema(400, "propor.invalido", "proposta é obrigatória");
        }
        return executar(pessoa.get(), () ->
                motor.propor(org.get(), UUID.fromString(id), req.proposta(), pessoa.get()));
    }

    @POST
    @Path("/{id}/concluir")
    public Response concluir(@PathParam("id") String id, ConcluirRequest req) {
        Optional<OrgId> org = contexto.atual();
        Optional<UUID> pessoa = contextoPessoa.atual();
        if (org.isEmpty() || pessoa.isEmpty()) {
            return problema(400, "requisicao.invalida", "X-Org-Id e X-Pessoa-Id são obrigatórios");
        }
        return executar(pessoa.get(), () -> motor.concluir(org.get(), UUID.fromString(id),
                req == null ? null : req.resultado(), pessoa.get()));
    }

    @POST
    @Path("/{id}/devolver")
    public Response devolver(@PathParam("id") String id, DevolverRequest req) {
        Optional<OrgId> org = contexto.atual();
        Optional<UUID> pessoa = contextoPessoa.atual();
        if (org.isEmpty() || pessoa.isEmpty()) {
            return problema(400, "requisicao.invalida", "X-Org-Id e X-Pessoa-Id são obrigatórios");
        }
        if (req == null || req.motivo() == null) {
            return problema(400, "devolver.sem_motivo", "motivo é obrigatório");
        }
        return executar(pessoa.get(), () ->
                motor.devolver(org.get(), UUID.fromString(id), req.motivo(), pessoa.get()));
    }

    /**
     * Fronteira de autorização (ADR-0013): retorna SÓ as delegações do executor
     * autenticado. A visão do gestor (todas do tenant) é rota à parte — jamais
     * este endpoint com um `?todos=true`.
     */
    @GET
    @Transactional
    public Response listar(@QueryParam("status") String status) {
        Optional<OrgId> org = contexto.atual();
        Optional<UUID> pessoa = contextoPessoa.atual();
        if (org.isEmpty() || pessoa.isEmpty()) {
            return problema(400, "requisicao.invalida", "X-Org-Id e X-Pessoa-Id são obrigatórios");
        }
        StringBuilder sql = new StringBuilder("""
                SELECT d.id, d.pendencia_id, d.dono_id, d.nivel, d.status, d.proposta,
                       d.prazo, EXTRACT(EPOCH FROM d.janela)::bigint,
                       p.titulo, p.quem_espera, p.o_que_trava, p.valor_em_jogo
                FROM delegacao d
                JOIN pendencia p ON p.id = d.pendencia_id AND p.org_id = d.org_id
                WHERE d.org_id = ? AND d.dono_id = ?
                """);
        if (status != null) {
            sql.append(" AND d.status = ?");
        }
        sql.append(" ORDER BY d.criada_em DESC");

        var q = em.createNativeQuery(sql.toString())
                .setParameter(1, org.get().valor()).setParameter(2, pessoa.get());
        if (status != null) {
            q.setParameter(3, status);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = q.getResultList();
        List<DelegacaoResumo> res = new ArrayList<>(linhas.size());
        for (Object[] l : linhas) {
            res.add(new DelegacaoResumo(l[0].toString(), l[1].toString(), l[2].toString(),
                    (String) l[3], (String) l[4], (String) l[5],
                    l[6] == null ? null : l[6].toString(), ((Number) l[7]).longValue(),
                    (String) l[8], (String) l[9], (String) l[10],
                    l[11] == null ? null : ((Number) l[11]).doubleValue()));
        }
        return Response.ok(res).build();
    }

    private interface Acao {
        void run();
    }

    private Response executar(UUID pessoa, Acao acao) {
        if (pessoa == null) {
            return problema(400, "pessoa.ausente", "X-Pessoa-Id é obrigatório");
        }
        try {
            acao.run();
            return Response.noContent().build();
        } catch (Falhas.NaoAutorizado e) {
            return problema(403, "delegacao.nao_e_sua", e.getMessage());
        } catch (Falhas.EstadoInvalido e) {
            return problema(409, "pendencia.estado_invalido", e.getMessage());
        }
    }

    private static Response problema(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new AutonomiaResource.Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record ProporRequest(String proposta) {
    }

    public record ConcluirRequest(String resultado) {
    }

    public record DevolverRequest(String motivo) {
    }

    public record DelegacaoResumo(String id, String pendenciaId, String donoId, String nivel,
                                  String status, String proposta, String prazo, long janelaSegundos,
                                  String titulo, String quemEspera, String oQueTrava, Double valorEmJogo) {
    }
}
