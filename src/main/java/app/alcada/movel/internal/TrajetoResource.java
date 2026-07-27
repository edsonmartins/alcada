package app.alcada.movel.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import app.alcada.movel.port.ConfigTrajeto;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.port.Outbox;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Modo trajeto (023) — resumo ao estacionar. {@code /liberar} solta todos os
 * efeitos represados do trajeto (o gestor confirmou o resumo → terceiros são
 * comunicados). {@code /desfazer} descarta o efeito represado de UMA pendência (o
 * terceiro nunca é comunicado). Só leitura/gestão do outbox, escopo por org (INV-15).
 */
@Path("/v1/trajeto")
@Produces(MediaType.APPLICATION_JSON)
public class TrajetoResource {

    private final Outbox outbox;
    private final ContextoTenant contexto;
    private final ConfigTrajeto config;

    public TrajetoResource(Outbox outbox, ContextoTenant contexto, ConfigTrajeto config) {
        this.outbox = outbox;
        this.contexto = contexto;
        this.config = config;
    }

    /** Config por tenant das classes/limiar recusáveis em movimento — o app aplica offline. */
    @GET
    @Path("/config")
    public Response config() {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        return respostaConfig(config.carregar(org.get()));
    }

    /**
     * Ajusta a config do trajeto da org (admin). Vale sem reiniciar (invalida o cache
     * pós-commit). Campo omitido (null) mantém o valor atual — atualização de um campo
     * só não zera o outro.
     *
     * <p>ATENÇÃO (segurança): não há checagem de papel aqui — no piloto (%demo, sem OIDC)
     * o tenant vem por header. Como isto reescreve a config de SEGURANÇA do trajeto de
     * toda a org, em %prod (OIDC) precisa ser restrito a um papel admin. Amarrado ao
     * endurecimento demo→prod (OIDC).
     */
    @PUT
    @Path("/config")
    @Transactional
    public Response salvarConfig(Config body) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (body == null) {
            return problema(400, "requisicao.invalida", "corpo é obrigatório");
        }
        ConfigTrajeto.Config atual = config.carregar(org.get());
        var classes = body.classesRecusaveis() != null ? body.classesRecusaveis() : atual.classesRecusaveis();
        var limite = body.valorLimite() != null ? body.valorLimite() : atual.valorLimite();
        return respostaConfig(config.salvar(org.get(), classes, limite));
    }

    private static Response respostaConfig(ConfigTrajeto.Config c) {
        return Response.ok(new Config(c.classesRecusaveis(), c.valorLimite())).build();
    }

    @POST
    @Path("/liberar")
    @Transactional
    public Response liberar(Req req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (req == null || req.trajetoId() == null || req.trajetoId().isBlank()) {
            return problema(400, "trajeto.ausente", "trajetoId é obrigatório");
        }
        outbox.liberarTrajeto(org.get(), UUID.fromString(req.trajetoId()));
        return Response.noContent().build();
    }

    @POST
    @Path("/desfazer")
    @Transactional
    public Response desfazer(Req req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (req == null || req.trajetoId() == null || req.trajetoId().isBlank()
                || req.pendenciaId() == null || req.pendenciaId().isBlank()) {
            return problema(400, "requisicao.invalida", "trajetoId e pendenciaId são obrigatórios");
        }
        outbox.descartarTrajeto(org.get(),
                UUID.fromString(req.trajetoId()), UUID.fromString(req.pendenciaId()));
        return Response.noContent().build();
    }

    private static Response problema(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record Req(String trajetoId, String pendenciaId) {
    }

    public record Config(List<String> classesRecusaveis, BigDecimal valorLimite) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
