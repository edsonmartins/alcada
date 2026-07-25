package app.alcada.regras.internal;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.regras.port.Mineracao;
import app.alcada.regras.port.Regras;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Regras de autonomia: propostas mineradas (com evidência), regras ativas e os
 * comandos de aceitar/silenciar/desativar. Nenhuma regra nasce sem aceite humano
 * (INV-10). Escopado por organização (INV-15).
 */
@Path("/v1/regras")
@Produces(MediaType.APPLICATION_JSON)
public class RegrasResource {

    // Autonomia: N1 é o mais autônomo (roteia sozinho); N3 o menos.
    private static final Map<String, Integer> RANK = Map.of("N3", 1, "N2", 2, "N1", 3);

    private final Mineracao mineracao;
    private final Regras regras;
    private final ContextoTenant contexto;

    public RegrasResource(Mineracao mineracao, Regras regras, ContextoTenant contexto) {
        this.mineracao = mineracao;
        this.regras = regras;
        this.contexto = contexto;
    }

    @GET
    @Transactional
    public Response ativas() {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        return Response.ok(regras.ativas(org.get())).build();
    }

    @GET
    @Path("/propostas")
    @Transactional
    public Response propostas() {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        return Response.ok(mineracao.propostas(org.get())).build();
    }

    @POST
    @Transactional
    public Response criar(CriarRegra req) {
        Optional<OrgId> orgOpt = contexto.atual();
        if (orgOpt.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (req == null || req.classe() == null || req.nivel() == null || req.donoId() == null) {
            return problema(400, "regra.invalida", "classe, nivel e donoId são obrigatórios");
        }
        if (!List.of("DECISAO", "BLOQUEIO", "ESTEIRA").contains(req.classe())
                || !RANK.containsKey(req.nivel())) {
            return problema(422, "regra.valor_invalido", "classe ou nivel inválido");
        }
        OrgId org = orgOpt.get();
        if (regras.existeRegraAtiva(org, req.classe())) {
            return problema(409, "regra.ja_ativa", "já existe regra ativa para a classe " + req.classe());
        }
        String max = regras.nivelMaximo(org, req.classe());
        if (max != null && RANK.get(req.nivel()) > RANK.getOrDefault(max, 3)) {
            return problema(422, "regra.acima_do_maximo",
                    "nivel " + req.nivel() + " excede o máximo da classe (" + max + ")");
        }
        UUID id = regras.criar(org, req.classe(), req.nivel(), UUID.fromString(req.donoId()));
        return Response.status(201).entity(new RegraCriada(id.toString())).build();
    }

    @POST
    @Path("/propostas/silenciar")
    @Transactional
    public Response silenciar(@HeaderParam("X-Pessoa-Id") String pessoa, SilenciarRegra req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (req == null || req.classe() == null) {
            return problema(400, "silenciar.sem_classe", "classe é obrigatória");
        }
        UUID por = pessoa == null ? null : UUID.fromString(pessoa);
        regras.silenciar(org.get(), req.classe(), por);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/desativar")
    @Transactional
    public Response desativar(@PathParam("id") String id) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        regras.desativar(org.get(), UUID.fromString(id));
        return Response.noContent().build();
    }

    private static Response problema(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record CriarRegra(String classe, String nivel, String donoId) {
    }

    public record RegraCriada(String id) {
    }

    public record SilenciarRegra(String classe) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
