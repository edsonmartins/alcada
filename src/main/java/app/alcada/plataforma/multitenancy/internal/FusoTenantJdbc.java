package app.alcada.plataforma.multitenancy.internal;

import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;

import app.alcada.plataforma.multitenancy.port.FusoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/**
 * Lê {@code organizacao.timezone}. A consulta é pela PK da própria organização
 * (não é tabela DADOS_TENANT), então não passa pela guarda de predicado org_id.
 * Cache em memória: o fuso de uma org raramente muda no ciclo de vida do processo.
 */
@ApplicationScoped
public class FusoTenantJdbc implements FusoTenant {

    private static final ZoneId PADRAO = ZoneId.of("America/Sao_Paulo");

    private final EntityManager em;
    private final ConcurrentHashMap<java.util.UUID, ZoneId> cache = new ConcurrentHashMap<>();

    public FusoTenantJdbc(EntityManager em) {
        this.em = em;
    }

    @Override
    public ZoneId fuso(OrgId org) {
        return cache.computeIfAbsent(org.valor(), this::carregar);
    }

    private ZoneId carregar(java.util.UUID orgId) {
        try {
            Object v = em.createNativeQuery("SELECT timezone FROM organizacao WHERE id = ?")
                    .setParameter(1, orgId).getSingleResult();
            return v == null ? PADRAO : ZoneId.of(v.toString());
        } catch (RuntimeException e) {
            // org inexistente ou fuso inválido: cai no padrão (nunca quebra a leitura).
            return PADRAO;
        }
    }
}
