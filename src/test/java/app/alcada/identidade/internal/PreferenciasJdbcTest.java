package app.alcada.identidade.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import app.alcada.identidade.port.Preferencias;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** Preferências do gestor (022, fatia C): aprende o nível de repasse, último vence. */
@QuarkusTest
class PreferenciasJdbcTest {

    @Inject Preferencias preferencias;
    @Inject EntityManager em;

    @Test
    void semHistoricoNaoTemPreferencia() {
        OrgId org = novaOrg();
        assertTrue(nivel(org, UUID.randomUUID()).isEmpty());
    }

    @Test
    void aprendeEDevolveONivelDeRepasse() {
        OrgId org = novaOrg();
        UUID gestor = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> preferencias.registrarNivelRepasse(org, gestor, "N1"));
        assertEquals("N1", nivel(org, gestor).orElseThrow());
    }

    @Test
    void ultimoNivelVence() {
        OrgId org = novaOrg();
        UUID gestor = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> preferencias.registrarNivelRepasse(org, gestor, "N1"));
        QuarkusTransaction.requiringNew().run(() -> preferencias.registrarNivelRepasse(org, gestor, "N3"));
        assertEquals("N3", nivel(org, gestor).orElseThrow());
    }

    private java.util.Optional<String> nivel(OrgId org, UUID gestor) {
        return QuarkusTransaction.requiringNew().call(() -> preferencias.nivelRepasse(org, gestor));
    }

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }
}
