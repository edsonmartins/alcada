package app.alcada.plataforma.multitenancy;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import app.alcada.identidade.internal.Pessoa;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/**
 * INV-15 — o {@code GuardaOrgId} faz falhar qualquer consulta a tabela sob
 * tenant que não carregue {@code org_id}.
 */
@QuarkusTest
class GuardaOrgIdTest {

    @Inject
    EntityManager em;

    @Test
    void query_sem_org_id_em_tabela_tenant_e_rejeitada() {
        Throwable erro = assertThrows(Throwable.class, () ->
                QuarkusTransaction.requiringNew().run(() ->
                        em.createQuery("select p from Pessoa p", Pessoa.class).getResultList()));
        assertTrue(mensagemContem(erro, "INV-15"),
                "esperava recusa por INV-15, veio: " + erro);
    }

    @Test
    void query_com_org_id_passa() {
        QuarkusTransaction.requiringNew().run(() -> {
            var r = em.createQuery("select p from Pessoa p where p.orgId = :o", Pessoa.class)
                    .setParameter("o", UUID.randomUUID())
                    .getResultList();
            assertNotNull(r);
        });
    }

    @Test
    void dispositivo_push_sem_org_id_e_rejeitado() {
        Throwable erro = assertThrows(Throwable.class, () ->
                QuarkusTransaction.requiringNew().run(() ->
                        em.createNativeQuery("select token_cifrado from dispositivo_push").getResultList()));
        assertTrue(mensagemContem(erro, "INV-15"),
                "esperava isolamento do token push por INV-15, veio: " + erro);
    }

    private static boolean mensagemContem(Throwable t, String trecho) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getMessage() != null && c.getMessage().contains(trecho)) {
                return true;
            }
        }
        return false;
    }
}
