package app.alcada.movel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import app.alcada.movel.port.Comando;
import app.alcada.movel.port.Comando.Intencao;
import app.alcada.movel.port.ComandoMovel;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.internal.WorkerOutbox;
import app.alcada.plataforma.outbox.port.Outbox;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/**
 * Represamento de trajeto (023, ADR-0014 §4, INV-14): efeitos externos ditados
 * EM_TRAJETO ficam represados no outbox até o gestor estacionar e liberar.
 */
@QuarkusTest
class TrajetoRepresamentoTest {

    @Inject ComandoMovel comandos;
    @Inject Outbox outbox;
    @Inject WorkerOutbox worker;
    @Inject EntityManager em;

    @Test
    void efeitoDeTrajetoNaoSaiAteLiberar() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org);
        UUID trajeto = UUID.randomUUID();

        comandos.sincronizar(org, UUID.randomUUID(),
                List.of(new Comando(UUID.randomUUID(), Intencao.RESOLVER, pend, null, trajeto)));

        assertEquals(1, pendentes(org, "item.fechado"), "efeito existe, represado");
        worker.processarLote();
        assertEquals(0, enviados(org, "item.fechado"), "represado não é emitido no trajeto");

        QuarkusTransaction.requiringNew().run(() -> outbox.liberarTrajeto(org, trajeto));
        worker.processarLote();
        assertEquals(1, enviados(org, "item.fechado"), "após liberar, o efeito sai");
    }

    @Test
    void desfazerNoResumoDescartaOEfeitoDoItem() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org);
        UUID trajeto = UUID.randomUUID();

        comandos.sincronizar(org, UUID.randomUUID(),
                List.of(new Comando(UUID.randomUUID(), Intencao.RESOLVER, pend, null, trajeto)));
        assertEquals(1, pendentes(org, "item.fechado"), "efeito represado existe");

        QuarkusTransaction.requiringNew().run(() -> outbox.descartarTrajeto(org, trajeto, pend));
        worker.processarLote();

        assertEquals(0, pendentes(org, "item.fechado"), "descartado: não fica represado");
        assertEquals(0, enviados(org, "item.fechado"), "e o terceiro nunca é comunicado");
    }

    @Test
    void semTrajetoEmiteNormalmente() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org);

        comandos.sincronizar(org, UUID.randomUUID(),
                List.of(new Comando(UUID.randomUUID(), Intencao.RESOLVER, pend, null)));
        worker.processarLote();

        assertEquals(1, enviados(org, "item.fechado"), "fora de trajeto, sai normalmente");
    }

    // ---- helpers -----------------------------------------------------------

    private long pendentes(OrgId org, String tipo) {
        return conta(org, tipo, "PENDENTE");
    }

    private long enviados(OrgId org, String tipo) {
        return conta(org, tipo, "ENVIADO");
    }

    private long conta(OrgId org, String tipo, String status) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT count(*) FROM outbox WHERE org_id = ? AND tipo = ? AND status = ?")
                .setParameter(1, org.valor()).setParameter(2, tipo).setParameter(3, status)
                .getSingleResult())).longValue();
    }

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    private UUID pendencia(OrgId org) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status, valor_em_jogo)
                VALUES (?, ?, 'Item', 'DECISAO', 'SEMANA', 'ENTRADA', 1000)
                """).setParameter(1, id).setParameter(2, org.valor()).executeUpdate());
        return id;
    }
}
