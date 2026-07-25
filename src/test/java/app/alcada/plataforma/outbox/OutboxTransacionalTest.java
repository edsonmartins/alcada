package app.alcada.plataforma.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import app.alcada.notificacao.internal.LinktorStub;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.internal.WorkerOutbox;
import app.alcada.plataforma.outbox.port.MensagemOutbox;
import app.alcada.plataforma.outbox.port.Outbox;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Outbox transacional (CLAUDE.md §4, INV-14), exercitado pelo Despachante de
 * produção + {@link LinktorStub}. Prova: o efeito NÃO sai se a transação de
 * origem reverte; é entregue ao menos uma vez; e falha de canal não perde o efeito.
 */
@QuarkusTest
class OutboxTransacionalTest {

    @Inject Outbox outbox;
    @Inject WorkerOutbox worker;
    @Inject LinktorStub linktor;
    @Inject EntityManager em;

    private OrgId org;

    @BeforeEach
    void setup() {
        org = new OrgId(UUID.randomUUID());
        linktor.limpar();
    }

    private MensagemOutbox resposta(String chave, String destino) {
        String payload = "{\"canal\":\"WHATSAPP\",\"destino\":\"" + destino + "\",\"texto\":\"ok\"}";
        return new MensagemOutbox(org, "canal.resposta", payload, chave);
    }

    @Test
    void efeito_externo_nao_sai_fora_da_transacao() {
        String chave = "rollback-" + UUID.randomUUID();
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                outbox.publicar(resposta(chave, "joao"));
                throw new RuntimeException("transação de origem revertida");
            });
        } catch (RuntimeException esperado) {
            // rollback
        }
        assertEquals(0, contarOutbox(chave), "linha do outbox não deveria existir após rollback");
        worker.processarLote();
        assertFalse(linktor.entregou(chave), "nenhum efeito externo pode ter saído");
    }

    @Test
    void entrega_ao_menos_uma_vez_e_idempotente() {
        String chave = "entrega-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> outbox.publicar(resposta(chave, "maria")));
        QuarkusTransaction.requiringNew().run(() -> outbox.publicar(resposta(chave, "maria")));
        assertEquals(1, contarOutbox(chave), "chave idempotente: uma linha só");

        worker.processarLote();
        assertTrue(linktor.entregou(chave), "deveria ter sido entregue");

        worker.processarLote(); // reprocessar não reentrega (linha ENVIADA)
        assertTrue(linktor.entregou(chave));
    }

    @Test
    void falha_na_entrega_nao_perde_o_efeito() {
        String chave = "retry-" + UUID.randomUUID();
        linktor.programarFalha("carlos");
        QuarkusTransaction.requiringNew().run(() -> outbox.publicar(resposta(chave, "carlos")));

        worker.processarLote(); // canal indisponível → falha
        assertFalse(linktor.entregou(chave));
        assertEquals(1, contarPendente(chave), "efeito preservado como PENDENTE (não perdido)");

        linktor.repararCanal("carlos");
        liberarBackoff(chave);
        worker.processarLote();
        assertTrue(linktor.entregou(chave), "entregue na retentativa");
    }

    private long contarOutbox(String chave) {
        return ((Number) em.createNativeQuery(
                "select count(*) from outbox where idempotency_key = ? and org_id = ?")
                .setParameter(1, chave).setParameter(2, org.valor()).getSingleResult()).longValue();
    }

    private long contarPendente(String chave) {
        return ((Number) em.createNativeQuery(
                "select count(*) from outbox where idempotency_key = ? and org_id = ? and status = 'PENDENTE'")
                .setParameter(1, chave).setParameter(2, org.valor()).getSingleResult()).longValue();
    }

    private void liberarBackoff(String chave) {
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery(
                        "update outbox set disponivel_em = now() where idempotency_key = ? and org_id = ?")
                        .setParameter(1, chave).setParameter(2, org.valor()).executeUpdate());
    }
}
