package app.alcada.movel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import app.alcada.movel.port.VozFeedback;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** Taxa de correção da voz (022): agregada por org (ADR-0017). */
@QuarkusTest
class VozFeedbackTest {

    @Inject VozFeedback feedback;
    @Inject EntityManager em;

    @Test
    void taxaEhCorrigidosSobreOTotal() {
        OrgId org = novaOrg();
        // 3 confirmados, 1 corrigido → taxa 0,25
        feedback.registrar(org, true);
        feedback.registrar(org, true);
        feedback.registrar(org, true);
        feedback.registrar(org, false);

        var t = feedback.taxa(org, 30);
        assertEquals(3, t.confirmados());
        assertEquals(1, t.corrigidos());
        assertEquals(0.25, t.taxa(), 0.0001);
    }

    @Test
    void semDadosTaxaZero() {
        var t = feedback.taxa(novaOrg(), 30);
        assertEquals(0, t.confirmados());
        assertEquals(0, t.corrigidos());
        assertEquals(0.0, t.taxa(), 0.0001);
    }

    @Test
    void isoladoPorOrganizacao() {
        OrgId a = novaOrg();
        OrgId b = novaOrg();
        feedback.registrar(a, false);
        assertEquals(0, feedback.taxa(b, 30).corrigidos(), "correção de outra org não vaza (INV-15)");
    }

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }
}
