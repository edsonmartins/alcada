package app.alcada.notificacao.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Tokens de portal externo (ADR-0013). Emissão/revogação internas; resolução
 * pública por hash. O banco guarda só o hash — vazar o banco não vaza tokens.
 */
@ApplicationScoped
public class PortalTokens {

    private final SecureRandom random = new SecureRandom();

    @ConfigProperty(name = "portal.folga-fechamento", defaultValue = "P7D")
    Duration folgaFechamento;

    private final EntityManager em;

    public PortalTokens(EntityManager em) {
        this.em = em;
    }

    /** Projeção pública — allowlist estrita (ADR-0013). Nada interno. */
    public record ProjecaoPublica(String estado, OffsetDateTime entrouEm,
                                  OffsetDateTime prazoPrevisto, String oQueFalta) {
    }

    public record TokenEmitido(UUID tokenId, String token) {
    }

    /** Emite um token cru (retornado uma única vez) e persiste só o hash. */
    public TokenEmitido emitir(OrgId org, UUID pendenciaId, String oQueFalta, OffsetDateTime expiraEm) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO token_portal (id, org_id, pendencia_id, token_hash, o_que_falta, expira_em)
                VALUES (?, ?, ?, ?, ?, ?)
                """)
                .setParameter(1, id).setParameter(2, org.valor()).setParameter(3, pendenciaId)
                .setParameter(4, hash(token)).setParameter(5, oQueFalta).setParameter(6, expiraEm)
                .executeUpdate();
        return new TokenEmitido(id, token);
    }

    public boolean revogar(OrgId org, UUID tokenId) {
        return em.createNativeQuery("UPDATE token_portal SET revogado = true WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, tokenId).executeUpdate() > 0;
    }

    /**
     * Resolve um token cru para a projeção pública. UMA consulta por hash (JOIN
     * com a pendência); toda a decisão de validade é em memória — latência
     * uniforme para inválido/expirado/revogado (todos devolvem {@code empty}).
     */
    public Optional<ProjecaoPublica> resolver(String token) {
        Object[] r;
        try {
            r = (Object[]) em.createNativeQuery("""
                    SELECT t.expira_em, t.revogado, t.o_que_falta,
                           p.status, p.fechada_em, p.criada_em, p.prazo_implicito
                    FROM token_portal t
                    JOIN pendencia p ON p.id = t.pendencia_id AND p.org_id = t.org_id
                    WHERE t.token_hash = ?
                    """)
                    .setParameter(1, hash(token))
                    .getSingleResult();
        } catch (NoResultException e) {
            return Optional.empty(); // token inválido
        }

        OffsetDateTime expiraEm = toOdt(r[0]);
        boolean revogado = (Boolean) r[1];
        String oQueFalta = (String) r[2];
        String status = (String) r[3];
        OffsetDateTime fechadaEm = toOdt(r[4]);
        OffsetDateTime criadaEm = toOdt(r[5]);
        OffsetDateTime prazo = toOdt(r[6]);

        OffsetDateTime agora = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        OffsetDateTime expiracaoEfetiva = expiraEm;
        if ("FECHADA".equals(status) && fechadaEm != null) {
            OffsetDateTime porFechamento = fechadaEm.plus(folgaFechamento);
            if (porFechamento.isBefore(expiracaoEfetiva)) {
                expiracaoEfetiva = porFechamento;
            }
        }
        if (revogado || agora.isAfter(expiracaoEfetiva)) {
            return Optional.empty(); // revogado ou expirado — mesmo caminho, sem I/O extra
        }

        String estadoPublico = "FECHADA".equals(status) ? "concluido" : "em_andamento";
        return Optional.of(new ProjecaoPublica(estadoPublico, criadaEm, prazo, oQueFalta));
    }

    private static OffsetDateTime toOdt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof OffsetDateTime odt) {
            return odt;
        }
        if (o instanceof java.time.Instant i) {
            return i.atOffset(java.time.ZoneOffset.UTC);
        }
        if (o instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(o.toString());
    }

    private static String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
