package app.alcada.identidade.internal;

import java.util.Optional;
import java.util.UUID;

import app.alcada.identidade.port.Preferencias;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

/**
 * Preferências do gestor em {@code preferencia_gestor} (INV-15: filtro de org em
 * toda query, sob o GuardaOrgId). Último valor vence — reflete o hábito recente.
 */
@ApplicationScoped
public class PreferenciasJdbc implements Preferencias {

    private static final String NIVEL_REPASSE = "nivel_repasse";

    private final EntityManager em;

    public PreferenciasJdbc(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<String> nivelRepasse(OrgId org, UUID gestorId) {
        return ler(org, gestorId, NIVEL_REPASSE);
    }

    @Override
    public void registrarNivelRepasse(OrgId org, UUID gestorId, String nivel) {
        if (nivel == null || nivel.isBlank()) {
            return;
        }
        gravar(org, gestorId, NIVEL_REPASSE, nivel);
    }

    private Optional<String> ler(OrgId org, UUID gestorId, String chave) {
        try {
            Object v = em.createNativeQuery("""
                    SELECT valor FROM preferencia_gestor
                    WHERE org_id = ? AND gestor_id = ? AND chave = ?
                    """)
                    .setParameter(1, org.valor())
                    .setParameter(2, gestorId)
                    .setParameter(3, chave)
                    .getSingleResult();
            return Optional.ofNullable(v == null ? null : v.toString());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    private void gravar(OrgId org, UUID gestorId, String chave, String valor) {
        em.createNativeQuery("""
                INSERT INTO preferencia_gestor (org_id, gestor_id, chave, valor, atualizado_em)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (org_id, gestor_id, chave)
                DO UPDATE SET valor = EXCLUDED.valor, atualizado_em = now()
                """)
                .setParameter(1, org.valor())
                .setParameter(2, gestorId)
                .setParameter(3, chave)
                .setParameter(4, valor)
                .executeUpdate();
    }
}
