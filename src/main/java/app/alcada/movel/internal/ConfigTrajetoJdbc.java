package app.alcada.movel.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import app.alcada.movel.port.ConfigTrajeto;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/**
 * Lê a config de trajeto das colunas de {@code organizacao} (por PK, como o
 * {@code FusoTenant} — não é tabela DADOS_TENANT, não passa pela guarda de
 * predicado). Cache em memória: muda raramente. Org inexistente → default.
 */
@ApplicationScoped
public class ConfigTrajetoJdbc implements ConfigTrajeto {

    private static final Config PADRAO = new Config(List.of("BLOQUEIO"), new BigDecimal("50000"));

    private final EntityManager em;
    private final ConcurrentHashMap<UUID, Config> cache = new ConcurrentHashMap<>();

    public ConfigTrajetoJdbc(EntityManager em) {
        this.em = em;
    }

    /** Classes válidas (whitelist) — evita gravar valor arbitrário no array. */
    private static final java.util.Set<String> CLASSES = java.util.Set.of("DECISAO", "BLOQUEIO", "ESTEIRA");

    @Override
    public Config carregar(OrgId org) {
        return cache.computeIfAbsent(org.valor(), this::ler);
    }

    @Override
    public void salvar(OrgId org, List<String> classes, BigDecimal valorLimite) {
        List<String> validas = classes == null ? List.of()
                : classes.stream().map(c -> c == null ? "" : c.trim().toUpperCase())
                        .filter(CLASSES::contains).distinct().toList();
        if (validas.isEmpty()) {
            validas = PADRAO.classesRecusaveis();
        }
        BigDecimal limite = valorLimite == null || valorLimite.signum() < 0
                ? PADRAO.valorLimite() : valorLimite;
        String arrayLiteral = "{" + String.join(",", validas) + "}"; // classes são da whitelist
        em.createNativeQuery("""
                UPDATE organizacao
                SET trajeto_classes_recusaveis = CAST(? AS text[]), trajeto_valor_limite = ?
                WHERE id = ?
                """)
                .setParameter(1, arrayLiteral)
                .setParameter(2, limite)
                .setParameter(3, org.valor())
                .executeUpdate();
        cache.put(org.valor(), new Config(validas, limite)); // vale já, sem restart
    }

    private Config ler(UUID orgId) {
        try {
            Object[] r = (Object[]) em.createNativeQuery(
                    "SELECT trajeto_classes_recusaveis, trajeto_valor_limite FROM organizacao WHERE id = ?")
                    .setParameter(1, orgId).getSingleResult();
            List<String> classes = classesDe(r[0]);
            BigDecimal limite = r[1] == null ? PADRAO.valorLimite() : new BigDecimal(r[1].toString());
            return new Config(classes.isEmpty() ? PADRAO.classesRecusaveis() : classes, limite);
        } catch (RuntimeException e) {
            return PADRAO; // nunca quebra a leitura
        }
    }

    private static List<String> classesDe(Object arr) {
        if (arr instanceof String[] a) {
            return List.of(a);
        }
        if (arr instanceof Object[] a) {
            return java.util.Arrays.stream(a).map(String::valueOf).toList();
        }
        return PADRAO.classesRecusaveis();
    }
}
