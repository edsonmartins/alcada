package app.alcada.captura.internal;

import java.util.List;
import java.util.Optional;

import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Seleção de grupos (024 F1b): o gestor lista os grupos que o bot já viu e escolhe
 * quais controlar (opt-in — ADR-0011 §1). Só grupo ativo tem o conteúdo ingerido;
 * grupo não selecionado é descartado no webhook. Escopo por org (INV-15).
 */
@Path("/v1/grupos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GruposResource {

    private final ContextoTenant contexto;
    private final EntityManager em;

    public GruposResource(ContextoTenant contexto, EntityManager em) {
        this.contexto = contexto;
        this.em = em;
    }

    /** Grupos que o bot já viu nesta org, com o estado de acompanhamento. */
    @GET
    @SuppressWarnings("unchecked")
    public Response listar() {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "org não resolvida");
        }
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT grupo_id, nome, ativa, ultimo_visto
                FROM grupo_acompanhado WHERE org_id = ?
                ORDER BY ativa DESC, ultimo_visto DESC
                """).setParameter(1, org.get().valor()).getResultList();
        List<Grupo> grupos = linhas.stream()
                .map(r -> new Grupo((String) r[0], (String) r[1], (Boolean) r[2],
                        r[3] == null ? null : r[3].toString()))
                .toList();
        return Response.ok(grupos).build();
    }

    /** Ativa/desativa o acompanhamento de um grupo (opt-in com finalidade). */
    @PUT
    @Path("/{grupoId}")
    @Transactional
    public Response definir(@PathParam("grupoId") String grupoId, Ajuste ajuste) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "org não resolvida");
        }
        boolean ativa = ajuste != null && ajuste.ativa();
        int n = em.createNativeQuery("""
                UPDATE grupo_acompanhado SET ativa = ?, finalidade = COALESCE(?, finalidade)
                WHERE org_id = ? AND grupo_id = ?
                """)
                .setParameter(1, ativa)
                .setParameter(2, ajuste == null ? null : ajuste.finalidade())
                .setParameter(3, org.get().valor())
                .setParameter(4, grupoId)
                .executeUpdate();
        if (n == 0) {
            return problema(404, "grupo.desconhecido", "grupo ainda não visto pelo bot nesta org");
        }
        return Response.noContent().build();
    }

    private static Response problema(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record Grupo(String grupoId, String nome, boolean ativa, String ultimoVisto) {
    }

    public record Ajuste(boolean ativa, String finalidade) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
