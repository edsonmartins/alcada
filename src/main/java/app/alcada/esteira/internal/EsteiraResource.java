package app.alcada.esteira.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import app.alcada.esteira.port.EntradasEsteira.NovaEtapa;
import app.alcada.esteira.port.EntradasEsteira.NovoCriterio;
import app.alcada.esteira.port.Esteiras;
import app.alcada.esteira.port.MineracaoChecklist;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Esteiras, instâncias, checklist versionado e propostas (§B). Escopo por org (INV-15). */
@Path("/v1/esteiras")
@Produces(MediaType.APPLICATION_JSON)
public class EsteiraResource {

    private final Esteiras esteiras;
    private final MineracaoChecklist mineracao;
    private final ContextoTenant contexto;

    public EsteiraResource(Esteiras esteiras, MineracaoChecklist mineracao, ContextoTenant contexto) {
        this.esteiras = esteiras;
        this.mineracao = mineracao;
        this.contexto = contexto;
    }

    @GET
    @Transactional
    public Response listar() {
        Optional<OrgId> org = contexto.atual();
        return org.isEmpty() ? problema() : Response.ok(esteiras.listar(org.get())).build();
    }

    @POST
    @Transactional
    public Response criar(CriarEsteira req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema();
        }
        if (req == null || req.nome() == null || req.etapas() == null || req.etapas().isEmpty()) {
            return erro(400, "esteira.invalida", "nome e etapas são obrigatórios");
        }
        UUID id = esteiras.criar(org.get(), req.nome(), req.etapas());
        return Response.status(201).entity(new Criada(id.toString())).build();
    }

    @GET
    @Path("/{id}/instancias")
    @Transactional
    public Response instancias(@PathParam("id") String id, @QueryParam("etapa") String etapa) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema();
        }
        UUID etapaId = etapa == null || etapa.isBlank() ? null : UUID.fromString(etapa);
        return Response.ok(esteiras.instancias(org.get(), UUID.fromString(id), etapaId)).build();
    }

    @POST
    @Path("/{id}/instancias")
    @Transactional
    public Response criarInstancia(@PathParam("id") String id, NovaInstancia req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema();
        }
        if (req == null || req.entidadeExterna() == null) {
            return erro(400, "instancia.invalida", "entidadeExterna é obrigatória");
        }
        UUID iid = esteiras.criarInstancia(org.get(), UUID.fromString(id), req.entidadeExterna());
        return Response.status(201).entity(new Criada(iid.toString())).build();
    }

    @GET
    @Path("/{id}/checklist")
    @Transactional
    public Response checklist(@PathParam("id") String id) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema();
        }
        var chk = esteiras.checklistVigente(org.get(), UUID.fromString(id));
        return chk == null ? erro(404, "esteira.sem_etapa_gestor", "esteira sem etapa do gestor")
                : Response.ok(chk).build();
    }

    @POST
    @Path("/{id}/checklist")
    @Transactional
    public Response publicarChecklist(@PathParam("id") String id, PublicarChecklist req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema();
        }
        if (req == null || req.criterios() == null) {
            return erro(400, "checklist.invalido", "criterios é obrigatório");
        }
        int versao = esteiras.publicarChecklist(org.get(), UUID.fromString(id), req.criterios());
        return Response.status(201).entity(new VersaoPublicada(versao)).build();
    }

    @GET
    @Path("/{id}/checklist/propostas")
    @Transactional
    public Response propostas(@PathParam("id") String id) {
        Optional<OrgId> org = contexto.atual();
        return org.isEmpty() ? problema()
                : Response.ok(mineracao.propostas(org.get(), UUID.fromString(id))).build();
    }

    private static Response problema() {
        return erro(400, "org.ausente", "X-Org-Id não resolvido");
    }

    private static Response erro(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record CriarEsteira(String nome, List<NovaEtapa> etapas) {
    }

    public record NovaInstancia(String entidadeExterna) {
    }

    public record PublicarChecklist(List<NovoCriterio> criterios) {
    }

    public record Criada(String id) {
    }

    public record VersaoPublicada(int versao) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
