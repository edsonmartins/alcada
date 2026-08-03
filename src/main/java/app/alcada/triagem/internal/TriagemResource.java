package app.alcada.triagem.internal;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.ContextoPessoa;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.triagem.port.Triagem;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Ações de triagem sobre a pendência (docs/API.md): as quatro saídas e o adiar.
 * `repassar` fica no motor (002); `/hoje` é o {@link HojeResource}. Erros em
 * {@code application/problem+json}.
 *
 * <p>Classe sob {@code /v1/pendencias} (não {@code /v1}): senão é sombreada
 * pelos resources de prefixo mais específico sobre {@code /v1/pendencias/{id}}.
 */
@Path("/v1/pendencias")
@Produces(MediaType.APPLICATION_JSON)
public class TriagemResource {

    private final TriagemService triagem;
    private final ContextoTenant contexto;
    private final ContextoPessoa contextoPessoa;

    public TriagemResource(TriagemService triagem, ContextoTenant contexto, ContextoPessoa contextoPessoa) {
        this.triagem = triagem;
        this.contexto = contexto;
        this.contextoPessoa = contextoPessoa;
    }

    /**
     * Resolve o item e, opcionalmente, deixa um lembrete datado (RFC-0009): o
     * compromisso que sobra da decisão vira um item que dorme até a data.
     */
    @POST
    @Path("/{id}/resolver")
    public Response resolver(@PathParam("id") String id, ResolverRequest req) {
        LembreteRequest l = req == null ? null : req.lembrete();
        if (l != null && (l.quando() == null || l.texto() == null || l.texto().isBlank())) {
            return problema(400, "lembrete.invalido", "quando e texto são obrigatórios no lembrete");
        }
        Triagem.Lembrete lembrete;
        try {
            lembrete = l == null ? null
                    : new Triagem.Lembrete(OffsetDateTime.parse(l.quando()), l.texto());
        } catch (java.time.format.DateTimeParseException e) {
            return problema(400, "lembrete.invalido", "quando deve ser ISO-8601 com fuso");
        }
        return comContexto((org, gestor) -> {
            try {
                triagem.resolver(org, UUID.fromString(id), req == null ? null : req.nota(),
                        lembrete, gestor);
                return Response.noContent().build();
            } catch (IllegalArgumentException e) {
                return problema(422, "lembrete.invalido", e.getMessage());
            }
        });
    }

    @POST
    @Path("/{id}/descartar")
    public Response descartar(@PathParam("id") String id) {
        return comContexto((org, gestor) -> {
            triagem.descartar(org, UUID.fromString(id), gestor);
            return Response.noContent().build();
        });
    }

    @POST
    @Path("/{id}/reservar")
    public Response reservar(@PathParam("id") String id, ReservarRequest req) {
        if (req == null || req.agendadoPara() == null) {
            return problema(400, "reservar.invalido", "agendado_para é obrigatório");
        }
        return comContexto((org, gestor) -> {
            triagem.reservar(org, UUID.fromString(id), OffsetDateTime.parse(req.agendadoPara()), gestor);
            return Response.noContent().build();
        });
    }

    @POST
    @Path("/{id}/repousar")
    public Response repousar(@PathParam("id") String id, RepousarRequest req) {
        if (req == null || req.voltaEm() == null) {
            return problema(400, "repousar.invalido", "volta_em é obrigatório");
        }
        return comContexto((org, gestor) -> {
            triagem.repousar(org, UUID.fromString(id), OffsetDateTime.parse(req.voltaEm()), gestor);
            return Response.noContent().build();
        });
    }

    @POST
    @Path("/{id}/adiar")
    public Response adiar(@PathParam("id") String id, AdiarRequest req) {
        if (req == null || req.voltaEm() == null) {
            return problema(400, "adiar.sem_data", "volta_em é obrigatório (\"depois\" não é aceito)");
        }
        if (req.oQueFalta() == null) {
            return problema(400, "adiar.sem_motivo", "o_que_falta é obrigatório");
        }
        return comContexto((org, gestor) -> {
            try {
                String oferta = triagem.adiar(org, UUID.fromString(id),
                        OffsetDateTime.parse(req.voltaEm()), req.oQueFalta(), gestor);
                return Response.ok(new AdiarResposta(oferta)).build();
            } catch (FalhasTriagem.MotivoInvalido e) {
                return problema(422, "adiar.motivo_invalido", e.getMessage());
            }
        });
    }

    // ---- infra -------------------------------------------------------------

    private interface Acao {
        Response executar(OrgId org, UUID gestor);
    }

    private Response comContexto(Acao acao) {
        Optional<OrgId> org = contexto.atual();
        Optional<UUID> pessoa = contextoPessoa.atual();
        if (org.isEmpty() || pessoa.isEmpty()) {
            return problema(400, "requisicao.invalida", "X-Org-Id e X-Pessoa-Id são obrigatórios");
        }
        try {
            return acao.executar(org.get(), pessoa.get());
        } catch (FalhasTriagem.EstadoInvalido e) {
            return problema(409, "pendencia.estado_invalido", e.getMessage());
        }
    }

    private static Response problema(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    // ---- DTOs --------------------------------------------------------------
    public record ResolverRequest(String nota, LembreteRequest lembrete) {
    }

    /** {@code quando}: ISO-8601 com fuso, já resolvido pelo chamador (RFC-0009). */
    public record LembreteRequest(String quando, String texto) {
    }

    public record ReservarRequest(String agendadoPara, Boolean gerarDossie) {
    }

    public record RepousarRequest(String voltaEm) {
    }

    public record AdiarRequest(String voltaEm, String oQueFalta) {
    }

    public record AdiarResposta(String oferta) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
