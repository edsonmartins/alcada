package app.alcada.autonomia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import app.alcada.autonomia.internal.MotorAutonomia;
import app.alcada.autonomia.port.ContatosExternos;
import app.alcada.autonomia.port.DestinoRepasse;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.port.MensagemOutbox;
import app.alcada.plataforma.outbox.port.Outbox;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoRegistrado;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** Repasse com destino externo (RFC-0008, fatia F1.2): destino tipado + AVISO_REPASSE no outbox. */
@QuarkusTest
class RepasseExternoTest {

    @Inject MotorAutonomia motor;
    @Inject ContatosExternos contatos;
    @Inject Outbox outbox;
    @Inject ConsultaTrilha trilha;
    @Inject EntityManager em;

    // WHEN repasse interno THEN delegacao.dono_id, sem AVISO_REPASSE, trilha REPASSADA (regressão)
    @Test
    void repasse_interno_nao_enfileira_aviso() {
        Ctx c = novo();
        UUID dono = UUID.randomUUID();
        UUID deleg = motor.delegar(c.org, c.pend, new DestinoRepasse.Interno(dono), "N2", agora(), c.gestor);

        assertEquals(dono, donoId(c.org, deleg));
        assertNull(contatoId(c.org, deleg), "interno não grava contato_id");
        assertEquals(0L, countOutbox(c.org, "AVISO_REPASSE"), "interno não avisa por canal");
        assertTrue(tipos(c.org, c.pend).contains("REPASSADA"));
    }

    // WHEN repasse externo THEN delegacao.contato_id, 1 AVISO_REPASSE com payload, trilha REPASSADA
    @Test
    void repasse_externo_enfileira_aviso_com_payload() {
        Ctx c = novo();
        UUID contato = contatos.registrar(c.org, "Clécia", "WHATSAPP", "+5521999990000", c.gestor);
        UUID deleg = motor.delegar(c.org, c.pend, new DestinoRepasse.Externo(contato), "N2", agora(), c.gestor);

        assertEquals(contato, contatoId(c.org, deleg));
        assertNull(donoId(c.org, deleg), "externo não grava dono_id");
        assertEquals(1L, countOutbox(c.org, "AVISO_REPASSE"));
        assertTrue(tipos(c.org, c.pend).contains("REPASSADA"));

        String payload = payloadAviso(c.org);
        assertTrue(payload.contains(contato.toString()), "payload traz o contato");
        assertTrue(payload.contains(c.pend.toString()), "payload traz a pendência");
        assertTrue(payload.contains("WHATSAPP"), "payload traz o canal");
        assertTrue(payload.contains("+5521999990000"), "payload traz o endereço");
    }

    // WHEN o mesmo aviso é publicado de novo THEN idempotente (1 linha) — reprocesso não duplica
    @Test
    void aviso_repasse_e_idempotente() {
        Ctx c = novo();
        UUID contato = contatos.registrar(c.org, "Paulo", "EMAIL", "paulo@rioquality.com.br", c.gestor);
        UUID deleg = motor.delegar(c.org, c.pend, new DestinoRepasse.Externo(contato), "N2", agora(), c.gestor);
        assertEquals(1L, countOutbox(c.org, "AVISO_REPASSE"));

        // republica com a MESMA idempotency_key (delegacaoId:aviso_repasse) → ON CONFLICT DO NOTHING
        QuarkusTransaction.requiringNew().run(() -> outbox.publicar(
                new MensagemOutbox(c.org, "AVISO_REPASSE", "{\"dup\":true}", deleg + ":aviso_repasse")));
        assertEquals(1L, countOutbox(c.org, "AVISO_REPASSE"), "reprocesso não duplica o aviso");
    }

    // O schema não deixa gravar dono_id E contato_id juntos (CHECK XOR)
    @Test
    void check_impede_dono_e_contato_juntos() {
        Ctx c = novo();
        UUID contato = contatos.registrar(c.org, "Daniel", "WHATSAPP", "+5521988880000", c.gestor);
        assertThrows(Exception.class, () -> QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("""
                        INSERT INTO delegacao (id, org_id, pendencia_id, dono_id, contato_id, nivel,
                                               prazo, janela, escalonamento)
                        VALUES (?, ?, ?, ?, ?, 'N2', now(), interval '4 hours', interval '24 hours')
                        """)
                        .setParameter(1, UUID.randomUUID()).setParameter(2, c.org.valor())
                        .setParameter(3, c.pend).setParameter(4, UUID.randomUUID()).setParameter(5, contato)
                        .executeUpdate()));
    }

    // ---- helpers ----
    private record Ctx(OrgId org, UUID pend, UUID gestor) {}

    private Ctx novo() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID pend = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("INSERT INTO organizacao (id, nome, sku) VALUES (?, 'Org', 'CLOUD')")
                    .setParameter(1, org.valor()).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status)
                    VALUES (?, ?, 'Aprovar algo', 'DECISAO', 'SEMANA', 'ENTRADA')
                    """)
                    .setParameter(1, pend).setParameter(2, org.valor()).executeUpdate();
        });
        return new Ctx(org, pend, UUID.randomUUID());
    }

    private static OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private UUID donoId(OrgId org, UUID deleg) {
        return (UUID) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT dono_id FROM delegacao WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, deleg).getSingleResult());
    }

    private UUID contatoId(OrgId org, UUID deleg) {
        return (UUID) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT contato_id FROM delegacao WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, deleg).getSingleResult());
    }

    private long countOutbox(OrgId org, String tipo) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT count(*) FROM outbox WHERE org_id = ? AND tipo = ?")
                .setParameter(1, org.valor()).setParameter(2, tipo).getSingleResult())).longValue();
    }

    private String payloadAviso(OrgId org) {
        return (String) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT payload::text FROM outbox WHERE org_id = ? AND tipo = 'AVISO_REPASSE'")
                .setParameter(1, org.valor()).getSingleResult());
    }

    private List<String> tipos(OrgId org, UUID pend) {
        return QuarkusTransaction.requiringNew().call(() ->
                trilha.daPendencia(org, pend).stream().map(EventoRegistrado::tipo).toList());
    }
}
