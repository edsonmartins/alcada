package app.alcada.captura.internal;

import app.alcada.captura.port.AvisoGrupo;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Marca o aviso publicado em grupo_acompanhado (024 C6). Só avança aviso_em se
 * ainda nulo — idempotente: reentrega do efeito não reescreve o instante.
 */
@ApplicationScoped
public class AvisoGrupoJdbc implements AvisoGrupo {

    private final EntityManager em;

    public AvisoGrupoJdbc(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void marcarPublicado(OrgId org, String grupoId) {
        em.createNativeQuery("""
                UPDATE grupo_acompanhado SET aviso_em = now()
                WHERE org_id = ? AND grupo_id = ? AND aviso_em IS NULL
                """)
                .setParameter(1, org.valor()).setParameter(2, grupoId).executeUpdate();
    }
}
