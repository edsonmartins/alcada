package app.alcada.plataforma;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.FusoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** 002 — fuso por tenant. */
@QuarkusTest
class FusoTenantTest {

    @Inject FusoTenant fuso;
    @Inject EntityManager em;

    @Test
    void usa_o_timezone_configurado_da_organizacao() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "INSERT INTO organizacao (id, nome, timezone) VALUES (?, 'Org', 'America/Manaus')")
                .setParameter(1, org.valor()).executeUpdate());
        assertEquals(ZoneId.of("America/Manaus"), fuso.fuso(org));
    }

    @Test
    void default_e_sao_paulo_quando_nao_configurado() {
        OrgId org = new OrgId(UUID.randomUUID());
        // insert sem timezone → DEFAULT da coluna
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                .setParameter(1, org.valor()).executeUpdate());
        assertEquals(ZoneId.of("America/Sao_Paulo"), fuso.fuso(org));
    }

    @Test
    void org_inexistente_cai_no_padrao_sem_quebrar() {
        assertEquals(ZoneId.of("America/Sao_Paulo"), fuso.fuso(new OrgId(UUID.randomUUID())));
    }
}
