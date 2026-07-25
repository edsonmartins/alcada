package app.alcada.identidade.internal;

import java.util.UUID;

import app.alcada.identidade.port.Titulares;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/**
 * Pseudonimização LGPD do titular. Atualiza apenas {@code pessoa} (com filtro de
 * {@code org_id}, INV-15). A trilha imutável não é tocada: ela referencia por
 * {@code pessoa_id}, que não muda, então a cadeia permanece íntegra.
 */
@ApplicationScoped
public class PseudonimizadorPessoa implements Titulares {

    private final EntityManager em;

    public PseudonimizadorPessoa(EntityManager em) {
        this.em = em;
    }

    @Override
    public void pseudonimizar(OrgId org, UUID pessoaId) {
        Pessoa p = em.createQuery(
                        "select p from Pessoa p where p.orgId = :org and p.id = :id", Pessoa.class)
                .setParameter("org", org.valor())
                .setParameter("id", pessoaId)
                .getResultStream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("pessoa inexistente na organização"));

        p.nome = "PESSOA_" + Integer.toHexString(pessoaId.hashCode());
        p.email = null;
        // id inalterado — é a referência que a trilha usa.
    }
}
