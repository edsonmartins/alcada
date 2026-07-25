package app.alcada.notificacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import app.alcada.notificacao.internal.DespachanteCanal;
import app.alcada.notificacao.internal.LinktorStub;
import app.alcada.notificacao.internal.VarredorMortos;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.internal.WorkerOutbox;
import app.alcada.plataforma.outbox.port.MensagemOutbox;
import app.alcada.plataforma.outbox.port.Outbox;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoRegistrado;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Cenários do 006 — fechamento no canal de origem. */
@QuarkusTest
class FechamentoCanalTest {

    @Inject Outbox outbox;
    @Inject WorkerOutbox worker;
    @Inject VarredorMortos varredor;
    @Inject DespachanteCanal despachante;
    @Inject LinktorStub linktor;
    @Inject ConsultaTrilha trilha;
    @Inject EntityManager em;

    @BeforeEach
    void setup() {
        linktor.limpar();
    }

    @Test
    void item_fechado_avisa_o_canal_de_origem() {
        OrgId org = novaOrg();
        UUID pend = pendenciaComOrigem(org, "WHATSAPP", "rafael");
        publicarFechado(org, pend);

        worker.processarLote();

        assertTrue(linktor.entregou(pend + ":fechado"), "mensagem entregue ao canal");
        assertTrue(tipos(org, pend).contains("COMUNICADA"));
    }

    @Test
    void so_o_solicitante_sem_deliberacao_interna() {
        OrgId org = novaOrg();
        UUID pend = pendenciaComOrigem(org, "WHATSAPP", "rafael");
        publicarFechado(org, pend);
        worker.processarLote();

        String texto = linktor.enviadas().get(0).texto().toLowerCase();
        assertTrue(texto.contains("resolvida"), "estado/fechamento");
        assertFalse(texto.contains("delega") || texto.contains("gestor") || texto.contains("nivel"),
                "sem deliberação interna");
    }

    @Test
    void reentrega_nao_duplica_mensagem() {
        OrgId org = novaOrg();
        UUID pend = pendenciaComOrigem(org, "WHATSAPP", "rafael");
        MensagemOutbox m = new MensagemOutbox(org, "item.fechado",
                "{\"pendencia_id\":\"" + pend + "\"}", pend + ":fechado");

        QuarkusTransaction.requiringNew().run(() -> despachante.entregar(m));
        QuarkusTransaction.requiringNew().run(() -> despachante.entregar(m)); // reentrega

        assertEquals(1, linktor.enviadas().size(), "canal recebe uma única vez");
        assertEquals(1, tipos(org, pend).stream().filter("COMUNICADA"::equals).count(), "uma COMUNICADA");
    }

    @Test
    void falha_esgota_e_gera_falha_comunicacao() {
        OrgId org = novaOrg();
        UUID pend = pendenciaComOrigem(org, "WHATSAPP", "offline");
        linktor.programarFalha("offline");
        publicarFechado(org, pend);

        // força o esgotamento: tentativas no limite, uma passada falha → ERRO
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "UPDATE outbox SET tentativas = 7, disponivel_em = now() WHERE org_id = ? AND idempotency_key = ?")
                .setParameter(1, org.valor()).setParameter(2, pend + ":fechado").executeUpdate());
        worker.processarLote();
        assertEquals("ERRO", statusOutbox(org, pend + ":fechado"));

        int reg = varredor.varrer();
        assertEquals(1, reg);
        assertTrue(tipos(org, pend).contains("FALHA_COMUNICACAO"));
        assertEquals(0, varredor.varrer(), "varredor idempotente: não re-registra");
    }

    @Test
    void eventos_internos_nao_avisam_o_solicitante() {
        OrgId org = novaOrg();
        UUID pend = pendenciaComOrigem(org, "WHATSAPP", "rafael");
        QuarkusTransaction.requiringNew().run(() -> {
            outbox.publicar(new MensagemOutbox(org, "delegacao.executada",
                    "{\"pendencia_id\":\"" + pend + "\"}", pend + ":x1"));
            outbox.publicar(new MensagemOutbox(org, "delegacao.escalada",
                    "{\"pendencia_id\":\"" + pend + "\"}", pend + ":x2"));
            outbox.publicar(new MensagemOutbox(org, "delegacao.devolvida",
                    "{\"pendencia_id\":\"" + pend + "\"}", pend + ":x3"));
        });

        worker.processarLote();

        assertEquals(0, linktor.enviadas().size(), "nenhuma saída ao solicitante por evento interno");
        assertFalse(tipos(org, pend).contains("COMUNICADA"));
    }

    @Test
    void canal_resposta_e_entregue() {
        OrgId org = novaOrg();
        String chave = "resp-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> outbox.publicar(new MensagemOutbox(org, "canal.resposta",
                "{\"canal\":\"WHATSAPP\",\"destino\":\"rafael\",\"texto\":\"recebido\"}", chave)));

        worker.processarLote();
        assertTrue(linktor.entregou(chave));
    }

    @Test
    void item_sem_conversa_gera_comunicacao_impossivel() {
        OrgId org = novaOrg();
        UUID pend = pendenciaSemOrigem(org); // origem_thread null → sem conversa (ADR-0025)
        publicarFechado(org, pend);

        worker.processarLote();

        assertFalse(linktor.entregou(pend + ":fechado"), "não há canal para fechar");
        assertEquals("ENVIADO", statusOutbox(org, pend + ":fechado"), "não fica preso em retentativa");
        // "não havia canal" é COMUNICACAO_IMPOSSIVEL — nunca COMUNICADA nem FALHA_COMUNICACAO
        var t = tipos(org, pend);
        assertTrue(t.contains("COMUNICACAO_IMPOSSIVEL"));
        assertFalse(t.contains("COMUNICADA"));
        assertFalse(t.contains("FALHA_COMUNICACAO"));
    }

    // ---- helpers -----------------------------------------------------------

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    private UUID pendenciaComOrigem(OrgId org, String canal, String destino) {
        UUID pend = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status,
                                       origem_canal, origem_destino, origem_thread)
                VALUES (?, ?, 'Item', 'DECISAO', 'SEMANA', 'FECHADA', ?, ?, 'thread-1')
                """).setParameter(1, pend).setParameter(2, org.valor())
                .setParameter(3, canal).setParameter(4, destino).executeUpdate());
        return pend;
    }

    private UUID pendenciaSemOrigem(OrgId org) {
        UUID pend = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status)
                VALUES (?, ?, 'Escape manual', 'DECISAO', 'SEMANA', 'FECHADA')
                """).setParameter(1, pend).setParameter(2, org.valor()).executeUpdate());
        return pend;
    }

    private void publicarFechado(OrgId org, UUID pend) {
        QuarkusTransaction.requiringNew().run(() -> outbox.publicar(new MensagemOutbox(org, "item.fechado",
                "{\"pendencia_id\":\"" + pend + "\"}", pend + ":fechado")));
    }

    private String statusOutbox(OrgId org, String chave) {
        return (String) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT status FROM outbox WHERE org_id = ? AND idempotency_key = ?")
                .setParameter(1, org.valor()).setParameter(2, chave).getSingleResult());
    }

    private List<String> tipos(OrgId org, UUID pend) {
        return QuarkusTransaction.requiringNew().call(() ->
                trilha.daPendencia(org, pend).stream().map(EventoRegistrado::tipo).toList());
    }
}
