package app.alcada.plataforma.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import app.alcada.plataforma.gateway.port.ModelGateway;
import app.alcada.plataforma.gateway.port.Sensibilidade;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaExtracao;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/**
 * Roteamento por sensibilidade e SKU (ADR-0020 §4, RFC-0007): o que não pode
 * sair, não sai. Provado pela ausência de chamada ao transporte externo.
 */
@QuarkusTest
class RoteamentoTest {

    @Inject ModelGateway gateway;
    @Inject TransporteFake transporte;
    @Inject EntityManager em;

    @Test
    void tenant_soberano_nunca_sai_para_o_gateway_externo() {
        OrgId org = criarOrg("SOBERANO");
        transporte.reset();

        // Mesmo INTERNA (que iria para fora em Cloud), Soberano força local.
        assertThrows(UnsupportedOperationException.class, () -> gateway.extrair(new TarefaExtracao<>(
                org, Sensibilidade.INTERNA, UUID.randomUUID(), "texto", "{\"type\":\"object\"}", s -> s)));
        assertEquals(0, transporte.chamadas(), "nenhuma requisição externa para tenant Soberano");
    }

    @Test
    void classe_restrita_vai_para_local_mesmo_em_cloud() {
        OrgId org = criarOrg("CLOUD");
        transporte.reset();

        assertThrows(UnsupportedOperationException.class, () -> gateway.extrair(new TarefaExtracao<>(
                org, Sensibilidade.RESTRITA, UUID.randomUUID(), "texto", "{\"type\":\"object\"}", s -> s)));
        assertEquals(0, transporte.chamadas(), "RESTRITA nunca atravessa a fronteira externa");
    }

    private OrgId criarOrg(String sku) {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome, sku) VALUES (?, ?, ?)")
                        .setParameter(1, org.valor()).setParameter(2, "Org " + sku)
                        .setParameter(3, sku).executeUpdate());
        return org;
    }
}
