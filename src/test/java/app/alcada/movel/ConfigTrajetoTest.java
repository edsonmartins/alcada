package app.alcada.movel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import app.alcada.movel.port.ConfigTrajeto;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** Config por tenant do modo trajeto (023): default e override por org. */
@QuarkusTest
class ConfigTrajetoTest {

    @Inject ConfigTrajeto config;
    @Inject EntityManager em;

    @Test
    void orgNovaUsaODefault() {
        OrgId org = novaOrg();
        var c = config.carregar(org);
        assertEquals(1, c.classesRecusaveis().size());
        assertTrue(c.classesRecusaveis().contains("BLOQUEIO"));
        assertEquals(0, new BigDecimal("50000").compareTo(c.valorLimite()));
    }

    @Test
    void orgPodeSobrescreverClassesELimite() {
        OrgId org = novaOrg();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "UPDATE organizacao SET trajeto_classes_recusaveis = '{BLOQUEIO,DECISAO}', "
                        + "trajeto_valor_limite = 10000 WHERE id = ?")
                .setParameter(1, org.valor()).executeUpdate());
        var c = config.carregar(org);
        assertTrue(c.classesRecusaveis().contains("DECISAO"));
        assertEquals(0, new BigDecimal("10000").compareTo(c.valorLimite()));
    }

    @Test
    void salvarAtualizaEInvalidaOCache() {
        OrgId org = novaOrg();
        // força o cache com o default
        assertEquals(0, new BigDecimal("50000").compareTo(config.carregar(org).valorLimite()));
        // admin ajusta
        QuarkusTransaction.requiringNew().run(() ->
                config.salvar(org, java.util.List.of("DECISAO", "BLOQUEIO"), new BigDecimal("8000")));
        var c = config.carregar(org); // deve refletir sem restart (cache invalidado)
        assertTrue(c.classesRecusaveis().contains("DECISAO"));
        assertEquals(0, new BigDecimal("8000").compareTo(c.valorLimite()));
    }

    @Test
    void salvarIgnoraClasseInvalidaEValorNegativo() {
        OrgId org = novaOrg();
        QuarkusTransaction.requiringNew().run(() ->
                config.salvar(org, java.util.List.of("DECISAO", "XPTO"), new BigDecimal("-5")));
        var c = config.carregar(org);
        assertTrue(c.classesRecusaveis().contains("DECISAO"));
        assertTrue(!c.classesRecusaveis().contains("XPTO"));
        assertEquals(0, new BigDecimal("50000").compareTo(c.valorLimite()), "valor negativo → padrão");
    }

    @Test
    void rollbackNaoEnvenenaOCache() {
        OrgId org = novaOrg();
        config.carregar(org); // popula o cache com o default
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                config.salvar(org, java.util.List.of("DECISAO"), new BigDecimal("9000"));
                throw new IllegalStateException("força rollback");
            });
        } catch (IllegalStateException ignored) {
            // esperado
        }
        var c = config.carregar(org); // deve refletir o banco (revertido), não o valor não-commitado
        assertTrue(c.classesRecusaveis().contains("BLOQUEIO"));
        assertTrue(!c.classesRecusaveis().contains("DECISAO"), "rollback não vaza para o cache");
        assertEquals(0, new BigDecimal("50000").compareTo(c.valorLimite()));
    }

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }
}
