package app.alcada.esteira.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import app.alcada.esteira.port.PortalInstancia;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

/**
 * Portal de instância: emissão/revogação de token assinado e projeção pública
 * curada. Guarda só o hash do token; resposta uniforme para inválido/expirado/
 * revogado. Escopo por org_id resolvido do próprio token (INV-15).
 */
@ApplicationScoped
public class PortalInstanciaJdbc implements PortalInstancia {

    private final EntityManager em;
    private final SecureRandom random = new SecureRandom();

    public PortalInstanciaJdbc(EntityManager em) {
        this.em = em;
    }

    @Override
    public TokenEmitido emitir(OrgId org, UUID instanciaId, OffsetDateTime expiraEm) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO token_instancia (id, org_id, instancia_id, token_hash, expira_em)
                VALUES (?, ?, ?, ?, ?)
                """).setParameter(1, id).setParameter(2, org.valor()).setParameter(3, instanciaId)
                .setParameter(4, hash(token)).setParameter(5, expiraEm).executeUpdate();
        return new TokenEmitido(id.toString(), token);
    }

    @Override
    public boolean revogar(OrgId org, UUID tokenId) {
        return em.createNativeQuery("UPDATE token_instancia SET revogado = true WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, tokenId).executeUpdate() > 0;
    }

    @Override
    public Optional<EstadoInstancia> resolver(String tokenCru) {
        UUID[] ctx = contexto(tokenCru);
        if (ctx == null) {
            return Optional.empty();
        }
        UUID orgId = ctx[0];
        UUID instanciaId = ctx[1];

        Object[] i;
        try {
            i = (Object[]) em.createNativeQuery("""
                    SELECT e.nome, et.nome, i.entrou_em, i.entrou_em + et.sla, et.esteira_id
                    FROM instancia i
                    JOIN esteira e ON e.id = i.esteira_id AND e.org_id = i.org_id
                    LEFT JOIN etapa et ON et.id = i.etapa_atual_id AND et.org_id = i.org_id
                    WHERE i.org_id = ? AND i.id = ?
                    """).setParameter(1, orgId).setParameter(2, instanciaId).getSingleResult();
        } catch (NoResultException e) {
            return Optional.empty();
        }
        return Optional.of(new EstadoInstancia((String) i[0], (String) i[1],
                toOdt(i[2]), toOdt(i[3]), oQueFalta(orgId, (UUID) i[4])));
    }

    @Override
    public void autoavaliar(String tokenCru, List<Declaracao> declaracoes) {
        UUID[] ctx = contexto(tokenCru);
        if (ctx == null) {
            throw new NoSuchElementException("token inválido");
        }
        for (Declaracao d : declaracoes) {
            em.createNativeQuery("""
                    INSERT INTO autoavaliacao (org_id, instancia_id, criterio_chave, conforme)
                    VALUES (?, ?, ?, ?)
                    """).setParameter(1, ctx[0]).setParameter(2, ctx[1])
                    .setParameter(3, d.criterioChave()).setParameter(4, d.conforme()).executeUpdate();
        }
    }

    // ---- auxiliares ----------------------------------------------------------

    /** {org_id, instancia_id} se o token é válido (não expirado/revogado); senão null (uniforme). */
    private UUID[] contexto(String tokenCru) {
        Object[] t;
        try {
            t = (Object[]) em.createNativeQuery(
                    "SELECT org_id, instancia_id, expira_em, revogado FROM token_instancia WHERE token_hash = ?")
                    .setParameter(1, hash(tokenCru)).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
        boolean revogado = (Boolean) t[3];
        OffsetDateTime expira = toOdt(t[2]);
        if (revogado || expira == null || expira.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            return null;
        }
        return new UUID[] {(UUID) t[0], (UUID) t[1]};
    }

    /** "O que falta da contraparte" = critérios OBJETIVOS do checklist vigente da etapa do gestor. */
    private List<EstadoInstancia.ItemFalta> oQueFalta(UUID orgId, UUID esteiraId) {
        if (esteiraId == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> cs = em.createNativeQuery("""
                SELECT c.chave, c.descricao FROM criterio c
                JOIN checklist ck ON ck.id = c.checklist_id AND ck.org_id = c.org_id
                JOIN etapa et ON et.id = ck.etapa_id AND et.org_id = ck.org_id
                WHERE c.org_id = ? AND et.esteira_id = ? AND et.etapa_do_gestor AND c.tipo = 'OBJETIVO'
                  AND ck.versao = (SELECT max(versao) FROM checklist ck2 WHERE ck2.org_id = ck.org_id AND ck2.etapa_id = ck.etapa_id)
                ORDER BY c.chave
                """).setParameter(1, orgId).setParameter(2, esteiraId).getResultList();
        List<EstadoInstancia.ItemFalta> res = new ArrayList<>(cs.size());
        for (Object[] c : cs) {
            res.add(new EstadoInstancia.ItemFalta((String) c[0], (String) c[1]));
        }
        return res;
    }

    private static String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static OffsetDateTime toOdt(Object v) {
        if (v instanceof OffsetDateTime odt) {
            return odt;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (v instanceof java.time.Instant inst) {
            return inst.atOffset(ZoneOffset.UTC);
        }
        return null;
    }
}
