package app.alcada.notificacao.internal;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import app.alcada.notificacao.port.ContasCalendario;
import app.alcada.plataforma.cripto.port.Cofre;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

/** Contas de calendário em {@code conta_calendario}, com os tokens cifrados pelo {@link Cofre}. */
@ApplicationScoped
public class ContasCalendarioJdbc implements ContasCalendario {

    private final EntityManager em;
    private final Cofre cofre;

    public ContasCalendarioJdbc(EntityManager em, Cofre cofre) {
        this.em = em;
        this.cofre = cofre;
    }

    @Override
    @Transactional
    public Optional<Conta> doGestor(OrgId org, UUID gestorId) {
        try {
            Object[] r = (Object[]) em.createNativeQuery("""
                    SELECT provedor, access_token, refresh_token, expira_em, escopo
                    FROM conta_calendario WHERE org_id = ? AND pessoa_id = ?
                    """).setParameter(1, org.valor()).setParameter(2, gestorId).getSingleResult();
            return Optional.of(new Conta(
                    (String) r[0],
                    cofre.decifrar((String) r[1]),
                    r[2] == null ? null : cofre.decifrar((String) r[2]),
                    r[3] == null ? null : ((java.time.Instant) r[3]).atOffset(java.time.ZoneOffset.UTC),
                    (String) r[4]));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void salvar(OrgId org, UUID gestorId, Conta c) {
        em.createNativeQuery("""
                INSERT INTO conta_calendario (org_id, pessoa_id, provedor, access_token,
                                              refresh_token, expira_em, escopo)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (org_id, pessoa_id) DO UPDATE SET
                    provedor = EXCLUDED.provedor,
                    access_token = EXCLUDED.access_token,
                    refresh_token = coalesce(EXCLUDED.refresh_token, conta_calendario.refresh_token),
                    expira_em = EXCLUDED.expira_em,
                    escopo = EXCLUDED.escopo,
                    conectada_em = now()
                """)
                .setParameter(1, org.valor()).setParameter(2, gestorId)
                .setParameter(3, c.provedor())
                .setParameter(4, cofre.cifrar(c.accessToken()))
                .setParameter(5, c.refreshToken() == null ? null : cofre.cifrar(c.refreshToken()))
                .setParameter(6, c.expiraEm()).setParameter(7, c.escopo())
                .executeUpdate();
    }

    @Override
    @Transactional
    public void revogar(OrgId org, UUID gestorId) {
        em.createNativeQuery("DELETE FROM conta_calendario WHERE org_id = ? AND pessoa_id = ?")
                .setParameter(1, org.valor()).setParameter(2, gestorId)
                .executeUpdate();
    }

    /** Só para o adaptador renovar o access token sem perder o refresh guardado. */
    @Transactional
    public void atualizarAcesso(OrgId org, UUID gestorId, String accessToken, OffsetDateTime expiraEm) {
        em.createNativeQuery("""
                UPDATE conta_calendario SET access_token = ?, expira_em = ?
                WHERE org_id = ? AND pessoa_id = ?
                """)
                .setParameter(1, cofre.cifrar(accessToken)).setParameter(2, expiraEm)
                .setParameter(3, org.valor()).setParameter(4, gestorId)
                .executeUpdate();
    }
}
