package app.alcada.plataforma.gateway.internal;

import app.alcada.plataforma.gateway.port.Destino;
import app.alcada.plataforma.gateway.port.Sensibilidade;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

/**
 * Decide o destino de uma tarefa (RFC-0007):
 * <ul>
 *   <li>{@code RESTRITA} → sempre local;</li>
 *   <li>tenant com SKU {@code SOBERANO} → tudo local, qualquer sensibilidade;</li>
 *   <li>{@code PUBLICA} e {@code INTERNA} em tenant Cloud → externo.</li>
 * </ul>
 * Fail-closed: se o SKU do tenant não puder ser lido, não se roteia para fora.
 */
@ApplicationScoped
public class Roteador {

    private final EntityManager em;

    public Roteador(EntityManager em) {
        this.em = em;
    }

    public Destino decidir(OrgId org, Sensibilidade sensibilidade) {
        if (sensibilidade == Sensibilidade.RESTRITA) {
            return Destino.LOCAL;
        }
        if (soberano(org)) {
            return Destino.LOCAL;
        }
        return Destino.EXTERNO;
    }

    private boolean soberano(OrgId org) {
        try {
            String sku = (String) em.createNativeQuery("SELECT sku FROM organizacao WHERE id = ?")
                    .setParameter(1, org.valor())
                    .getSingleResult();
            return "SOBERANO".equalsIgnoreCase(sku);
        } catch (NoResultException e) {
            throw new IllegalStateException(
                    "SKU do tenant desconhecido — não se roteia para fora sem confirmar (fail-closed): " + org.valor());
        }
    }
}
