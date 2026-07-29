package app.alcada.captura.internal;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.ContextoPessoa;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
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
 * Fila (docs/API.md): lista de pendências filtrada por status e reversão de
 * fusão. Sempre escopado por organização (INV-15).
 */
@Path("/v1/pendencias")
@Produces(MediaType.APPLICATION_JSON)
public class PendenciaResource {

    private final EntityManager em;
    private final ContextoTenant contexto;
    private final ContextoPessoa contextoPessoa;
    private final Trilha trilha;
    private final app.alcada.captura.port.EscapeCaptura escape;

    public PendenciaResource(EntityManager em, ContextoTenant contexto, ContextoPessoa contextoPessoa,
                             Trilha trilha, app.alcada.captura.port.EscapeCaptura escape) {
        this.em = em;
        this.contexto = contexto;
        this.contextoPessoa = contextoPessoa;
        this.trilha = trilha;
        this.escape = escape;
    }

    /**
     * Escape manual (ADR-0005): cria pendência direto em ENTRADA, sem canal de
     * origem. É métrica de falha da captura — e o plano B da demo (injetar um
     * item sem depender do Linktor). Fechada depois → COMUNICACAO_IMPOSSIVEL
     * (não há conversa; ADR-0025).
     */
    @POST
    @Transactional
    public Response criar(EscapeRequest req) {
        Optional<OrgId> org = contexto.atual();
        Optional<UUID> pessoa = contextoPessoa.atual();
        if (org.isEmpty() || pessoa.isEmpty()) {
            return problema(400, "requisicao.invalida", "X-Org-Id e X-Pessoa-Id são obrigatórios");
        }
        if (req == null || req.titulo() == null || req.titulo().isBlank()) {
            return problema(400, "escape.sem_titulo", "titulo é obrigatório");
        }
        try {
            UUID id = escape.registrar(org.get(), req.titulo(), req.quemEspera(), req.oQueTrava(),
                    req.classe(), pessoa.get());
            return Response.status(201).entity(new EscapeCriada(id.toString())).build();
        } catch (IllegalArgumentException e) {
            return problema(422, "classe.invalida", e.getMessage());
        }
    }

    @GET
    @Transactional
    public Response listar(@QueryParam("status") String status) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.titulo, p.classe, p.horizonte, p.status, p.quem_espera, p.temperatura,
                       p.baixa_confianca, p.o_que_trava, p.valor_em_jogo, p.prazo_implicito, p.criada_em,
                       g.nome AS origem_grupo,
                       (SELECT count(*) FROM cobranca c
                          WHERE c.org_id = p.org_id AND c.pendencia_id = p.id) AS cobrancas
                FROM pendencia p
                LEFT JOIN grupo_acompanhado g
                       ON g.org_id = p.org_id AND g.grupo_id = p.origem_thread
                WHERE p.org_id = ?
                """);
        if (status != null && !status.isBlank()) {
            sql.append(" AND p.status = ?");
        }
        sql.append(" ORDER BY p.criada_em DESC");

        var q = em.createNativeQuery(sql.toString()).setParameter(1, org.get().valor());
        if (status != null && !status.isBlank()) {
            q.setParameter(2, status);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = q.getResultList();

        List<PendenciaResumo> fila = new ArrayList<>(linhas.size());
        for (Object[] l : linhas) {
            fila.add(new PendenciaResumo(l[0].toString(), (String) l[1], (String) l[2], (String) l[3],
                    (String) l[4], (String) l[5], ((Number) l[6]).intValue(), (Boolean) l[7],
                    (String) l[8], l[9] == null ? null : ((Number) l[9]).doubleValue(),
                    toOdt(l[10]), toOdt(l[11]),
                    (String) l[12], ((Number) l[13]).intValue()));
        }
        return Response.ok(fila).build();
    }

    @POST
    @Path("/{id}/desfundir")
    @Transactional
    public Response desfundir(@PathParam("id") String pendenciaId,
                              DesfundirRequest req) {
        Optional<OrgId> orgOpt = contexto.atual();
        if (orgOpt.isEmpty()) {
            return problema(400, "org.ausente", "X-Org-Id não resolvido");
        }
        UUID pessoaId = contextoPessoa.atual().orElse(null);
        if (req == null || req.cobrancaId() == null) {
            return problema(400, "desfundir.sem_cobranca", "cobranca_id é obrigatório");
        }
        OrgId org = orgOpt.get();
        UUID cobrancaId = UUID.fromString(req.cobrancaId());

        Object[] cob;
        try {
            cob = (Object[]) em.createNativeQuery(
                    "SELECT pendencia_id, evento_bruto_id FROM cobranca WHERE org_id = ? AND id = ?")
                    .setParameter(1, org.valor()).setParameter(2, cobrancaId).getSingleResult();
        } catch (NoResultException e) {
            return problema(404, "cobranca.inexistente", "cobrança não encontrada");
        }
        UUID original = (UUID) cob[0];
        UUID eventoBrutoId = (UUID) cob[1];

        String texto = (String) em.createNativeQuery(
                "SELECT texto FROM evento_bruto WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, eventoBrutoId).getSingleResult();

        // desvincula a cobrança e esfria a original
        em.createNativeQuery("DELETE FROM cobranca WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, cobrancaId).executeUpdate();
        em.createNativeQuery(
                "UPDATE pendencia SET temperatura = greatest(temperatura - 1, 0) WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, original).executeUpdate();

        // cria pendência independente a partir do bruto
        UUID nova = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status)
                VALUES (?, ?, ?, 'DECISAO', 'SEMANA', 'ENTRADA')
                """)
                .setParameter(1, nova).setParameter(2, org.valor())
                .setParameter(3, resumo(texto)).executeUpdate();

        // trilha em ambos os itens
        Ator ator = pessoaId != null ? Ator.humano(pessoaId) : Ator.sistemaMotor("captura");
        String carga = "{\"cobranca_id\":\"" + cobrancaId + "\",\"nova_pendencia_id\":\"" + nova + "\"}";
        trilha.registrar(new EventoTrilha(org, original, TipoEvento.DESFUNDIDA, ator, null, null, null, carga));
        trilha.registrar(new EventoTrilha(org, nova, TipoEvento.CAPTADA, ator, null, "ENTRADA",
                "{\"origem\":\"desfusao\",\"de\":\"" + original + "\"}", null));
        // (o par serve de sinal negativo para o limiar do dedup — não persistido nesta fase)

        return Response.ok(new DesfundirResposta(nova.toString())).build();
    }

    private static Response problema(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    private static String resumo(String texto) {
        if (texto == null || texto.isBlank()) {
            return "(sem texto)";
        }
        String t = texto.strip();
        return t.length() <= 80 ? t : t.substring(0, 80) + "…";
    }

    public record PendenciaResumo(String id, String titulo, String classe, String horizonte,
                                  String status, String quemEspera, int temperatura, boolean baixaConfianca,
                                  String oQueTrava, Double valorEmJogo,
                                  OffsetDateTime prazoImplicito, OffsetDateTime criadaEm,
                                  String origemGrupo, int cobrancas) {
    }

    /** timestamptz volta como Instant/Timestamp/OffsetDateTime conforme o driver; normaliza. */
    private static OffsetDateTime toOdt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof OffsetDateTime odt) {
            return odt;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        if (v instanceof java.time.Instant inst) {
            return inst.atOffset(java.time.ZoneOffset.UTC);
        }
        return null;
    }

    public record EscapeRequest(String titulo, String quemEspera, String oQueTrava, String classe) {
    }

    public record EscapeCriada(String id) {
    }

    public record DesfundirRequest(String cobrancaId) {
    }

    public record DesfundirResposta(String novaPendenciaId) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
