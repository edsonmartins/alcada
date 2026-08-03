package app.alcada.notificacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import app.alcada.autonomia.internal.MotorAutonomia;
import app.alcada.autonomia.port.ContatosExternos;
import app.alcada.autonomia.port.DestinoRepasse;
import app.alcada.notificacao.internal.LinktorStub;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.internal.WorkerOutbox;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoRegistrado;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Entrega do aviso de repasse a contato externo (RFC-0008, fatia F1.3a). */
@QuarkusTest
class RepasseAvisoTest {

    @Inject MotorAutonomia motor;
    @Inject ContatosExternos contatos;
    @Inject WorkerOutbox worker;
    @Inject LinktorStub linktor;
    @Inject ConsultaTrilha trilha;
    @Inject EntityManager em;

    @BeforeEach
    void limpar() {
        linktor.limpar();
    }

    // WHEN repasse externo (WhatsApp) THEN o worker entrega no canal + trilha COMUNICADA
    @Test
    void aviso_externo_entrega_no_canal_e_registra_comunicada() {
        Ctx c = novo("chan-abc");
        UUID contato = contatos.registrar(c.org, "Clécia", "WHATSAPP", "+5521999990000", c.gestor);
        motor.delegar(c.org, c.pend, new DestinoRepasse.Externo(contato), "N2", agora(), c.gestor);

        worker.processarLote();

        var diretas = linktor.diretas().stream().filter(d -> "+5521999990000".equals(d.to())).toList();
        assertEquals(1, diretas.size(), "enviou 1 mensagem direta ao contato");
        assertEquals("chan-abc", diretas.get(0).channelId(), "usa o canal WhatsApp do tenant");
        assertTrue(diretas.get(0).texto().contains(c.pend.toString()), "texto referencia a pendência");
        assertTrue(tipos(c.org, c.pend).contains("COMUNICADA"));
    }

    // WHEN reprocessa THEN não reenvia (idempotente por idempotency_key)
    @Test
    void aviso_e_idempotente_no_reprocesso() {
        Ctx c = novo("chan-abc");
        UUID contato = contatos.registrar(c.org, "Paulo", "WHATSAPP", "+5521988880000", c.gestor);
        motor.delegar(c.org, c.pend, new DestinoRepasse.Externo(contato), "N2", agora(), c.gestor);

        worker.processarLote();
        worker.processarLote();

        assertEquals(1, linktor.diretas().stream().filter(d -> "+5521988880000".equals(d.to())).count());
    }

    // WHEN o canal falha THEN não comunica e o aviso fica pendente (reprocessa)
    @Test
    void canal_indisponivel_nao_comunica_e_reprocessa() {
        Ctx c = novo("chan-abc");
        UUID contato = contatos.registrar(c.org, "Daniel", "WHATSAPP", "+5521977770000", c.gestor);
        linktor.programarFalha("+5521977770000");
        motor.delegar(c.org, c.pend, new DestinoRepasse.Externo(contato), "N2", agora(), c.gestor);

        worker.processarLote();

        assertEquals(0, linktor.diretas().stream().filter(d -> "+5521977770000".equals(d.to())).count());
        assertFalse(tipos(c.org, c.pend).contains("COMUNICADA"), "não comunica se o canal falhou");
        assertTrue(pendentes(c.org) >= 1, "o aviso continua pendente para reprocesso");
    }

    // ---- helpers ----
    private record Ctx(OrgId org, UUID pend, UUID gestor) {}

    private Ctx novo(String channelId) {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID pend = UUID.randomUUID();
        UUID gestor = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("INSERT INTO organizacao (id, nome, sku) VALUES (?, 'Org', 'CLOUD')")
                    .setParameter(1, org.valor()).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status)
                    VALUES (?, ?, 'Aprovar algo', 'DECISAO', 'SEMANA', 'ENTRADA')
                    """)
                    .setParameter(1, pend).setParameter(2, org.valor()).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO fonte (id, org_id, tipo, identificador, ativa, segredo, linktor_channel_id)
                    VALUES (?, ?, 'WHATSAPP', 'grupo-teste', true, 'seg', ?)
                    """)
                    .setParameter(1, UUID.randomUUID()).setParameter(2, org.valor())
                    .setParameter(3, channelId).executeUpdate();
        });
        return new Ctx(org, pend, gestor);
    }

    private static OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private long pendentes(OrgId org) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT count(*) FROM outbox WHERE org_id = ? AND tipo = 'AVISO_REPASSE' AND status = 'PENDENTE'")
                .setParameter(1, org.valor()).getSingleResult())).longValue();
    }

    private List<String> tipos(OrgId org, UUID pend) {
        return QuarkusTransaction.requiringNew().call(() ->
                trilha.daPendencia(org, pend).stream().map(EventoRegistrado::tipo).toList());
    }
}
