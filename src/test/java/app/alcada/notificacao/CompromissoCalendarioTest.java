package app.alcada.notificacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import app.alcada.notificacao.internal.CalendarioStub;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.internal.WorkerOutbox;
import app.alcada.plataforma.outbox.port.Outbox;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoRegistrado;
import app.alcada.triagem.internal.TriagemService;
import app.alcada.triagem.port.Triagem;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Compromisso na agenda do gestor (RFC-0009, fatia F2.3): o lembrete com
 * calendário vira efeito externo pelo outbox — só sai depois da janela, e
 * desfazer antes disso significa que a agenda nunca soube (INV-14).
 */
@QuarkusTest
class CompromissoCalendarioTest {

    @Inject TriagemService triagem;
    @Inject WorkerOutbox worker;
    @Inject CalendarioStub calendario;
    @Inject Outbox outbox;
    @Inject ConsultaTrilha trilha;
    @Inject EntityManager em;

    @BeforeEach
    void limpar() {
        calendario.limpar();
    }

    // C13 — o compromisso entra na agenda e a trilha registra
    @Test
    void compromisso_entra_no_calendario_depois_da_janela() {
        Ctx c = novo();
        OffsetDateTime quinta = agora().plusDays(3);
        resolverComCompromisso(c, quinta, "Reunião Sharpi");

        // Antes da janela, o worker não emite: o efeito espera.
        worker.processarLote();
        assertTrue(calendario.eventos().isEmpty(), "nada sai antes da janela (INV-14)");
        assertEquals("PENDENTE", statusOutbox(c.org));

        liberarJanela(c.org);
        worker.processarLote();

        assertEquals(1, calendario.eventos().size());
        var evento = calendario.eventos().get(0);
        assertEquals(c.gestor, evento.gestorId(), "a agenda é do gestor, não do tenant");
        assertEquals(quinta.toInstant(), evento.quando().toInstant());
        assertEquals("Reunião Sharpi", evento.titulo());

        UUID lembrete = lembreteDe(c.org, c.pend);
        assertNotNull(eventoIdDe(c.org, lembrete), "guarda o id do evento no provedor");
        assertTrue(tipos(c.org, lembrete).contains("COMPROMISSO_AGENDADO"));
    }

    // C14 — desfazer dentro da janela: a agenda nunca soube
    @Test
    void descartar_na_janela_impede_o_evento() {
        Ctx c = novo();
        resolverComCompromisso(c, agora().plusDays(3), "Reunião Sharpi");
        UUID lembrete = lembreteDe(c.org, c.pend);

        boolean descartado = QuarkusTransaction.requiringNew().call(() -> outbox.descartarPendente(
                c.org, Triagem.chaveCompromisso(lembrete)));

        assertTrue(descartado, "o efeito ainda não tinha saído");
        liberarJanela(c.org);
        worker.processarLote();
        assertTrue(calendario.eventos().isEmpty(), "nenhum evento foi criado");
        assertFalse(tipos(c.org, lembrete).contains("COMPROMISSO_AGENDADO"));
    }

    // C15 — sem calendário conectado: registra a impossibilidade, sem ficar preso
    @Test
    void sem_conta_conectada_registra_falha_e_nao_repete() {
        Ctx c = novo();
        calendario.programarSemConta(c.gestor);
        resolverComCompromisso(c, agora().plusDays(3), "Reunião Sharpi");
        liberarJanela(c.org);

        worker.processarLote();

        UUID lembrete = lembreteDe(c.org, c.pend);
        assertTrue(tipos(c.org, lembrete).contains("FALHA_COMPROMISSO"));
        assertEquals("ENVIADO", statusOutbox(c.org), "não fica em retentativa eterna");
        assertNull(eventoIdDe(c.org, lembrete));
    }

    // C15 — provedor fora do ar: volta para retentativa (INV-13)
    @Test
    void provedor_indisponivel_reprocessa() {
        Ctx c = novo();
        calendario.programarFalha(c.gestor);
        resolverComCompromisso(c, agora().plusDays(3), "Reunião Sharpi");
        liberarJanela(c.org);

        worker.processarLote();

        assertEquals("PENDENTE", statusOutbox(c.org), "segue pendente para nova tentativa");
        assertTrue(calendario.eventos().isEmpty());
    }

    // Reprocesso não duplica o evento na agenda (INV-13)
    @Test
    void reprocesso_nao_duplica_o_evento() {
        Ctx c = novo();
        resolverComCompromisso(c, agora().plusDays(3), "Reunião Sharpi");
        liberarJanela(c.org);
        worker.processarLote();

        // devolve a linha para PENDENTE, como faria um retry após falha na marcação
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "UPDATE outbox SET status = 'PENDENTE' WHERE org_id = ? AND tipo = 'EVENTO_CALENDARIO'")
                .setParameter(1, c.org.valor()).executeUpdate());
        worker.processarLote();

        assertEquals(1, calendario.eventos().size(), "um evento só");
    }

    // C14b — cancelar o lembrete depois do evento existir: sai da agenda
    @Test
    void cancelar_lembrete_depois_do_evento_remove_da_agenda() {
        Ctx c = novo();
        resolverComCompromisso(c, agora().plusDays(3), "Reunião Sharpi");
        liberarJanela(c.org);
        worker.processarLote();
        UUID lembrete = lembreteDe(c.org, c.pend);
        String eventoId = eventoIdDe(c.org, lembrete);

        triagem.cancelarLembrete(c.org, lembrete, c.gestor);
        worker.processarLote();

        assertTrue(calendario.cancelados().contains(eventoId), "o evento foi removido");
        assertNull(eventoIdDe(c.org, lembrete), "e a pendência não aponta mais para ele");
        assertEquals("FECHADA", statusDe(c.org, lembrete));
        assertTrue(tipos(c.org, lembrete).contains("DESCARTADA"));
    }

    // C14 — cancelar antes da janela vencer: o evento nunca chega a existir
    @Test
    void cancelar_lembrete_na_janela_descarta_o_efeito() {
        Ctx c = novo();
        resolverComCompromisso(c, agora().plusDays(3), "Reunião Sharpi");
        UUID lembrete = lembreteDe(c.org, c.pend);

        triagem.cancelarLembrete(c.org, lembrete, c.gestor);
        liberarJanela(c.org);
        worker.processarLote();

        assertTrue(calendario.eventos().isEmpty(), "a agenda nunca soube");
        assertEquals("FECHADA", statusDe(c.org, lembrete));
    }

    // Cancelar duas vezes não faz nada demais (idempotente)
    @Test
    void cancelar_lembrete_e_idempotente() {
        Ctx c = novo();
        resolverComCompromisso(c, agora().plusDays(3), "Reunião Sharpi");
        UUID lembrete = lembreteDe(c.org, c.pend);

        triagem.cancelarLembrete(c.org, lembrete, c.gestor);
        triagem.cancelarLembrete(c.org, lembrete, c.gestor);

        assertEquals(1, tipos(c.org, lembrete).stream().filter("DESCARTADA"::equals).count());
    }

    // Lembrete sem calendário não gera efeito externo nenhum
    @Test
    void lembrete_sem_calendario_nao_publica_efeito() {
        Ctx c = novo();
        triagem.resolver(c.org, c.pend, null,
                new Triagem.Lembrete(agora().plusDays(3), "Reunião Sharpi"), c.gestor);

        assertEquals(0L, quantosCompromissos(c.org));
    }

    // ---- helpers -----------------------------------------------------------

    private record Ctx(OrgId org, UUID pend, UUID gestor) {}

    private Ctx novo() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID pend = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                    .setParameter(1, org.valor()).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status)
                    VALUES (?, ?, 'Proposta Sharpi', 'DECISAO', 'SEMANA', 'ENTRADA')
                    """).setParameter(1, pend).setParameter(2, org.valor()).executeUpdate();
        });
        return new Ctx(org, pend, UUID.randomUUID());
    }

    private void resolverComCompromisso(Ctx c, OffsetDateTime quando, String texto) {
        triagem.resolver(c.org, c.pend, null, new Triagem.Lembrete(quando, texto, true), c.gestor);
    }

    /** Adianta o relógio do efeito: simula a janela vencida. */
    private void liberarJanela(OrgId org) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                UPDATE outbox SET disponivel_em = now() - interval '1 minute'
                WHERE org_id = ? AND tipo = 'EVENTO_CALENDARIO'
                """).setParameter(1, org.valor()).executeUpdate());
    }

    private static OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String statusDe(OrgId org, UUID pendenciaId) {
        return (String) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT status FROM pendencia WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, pendenciaId).getSingleResult());
    }

    private String statusOutbox(OrgId org) {
        return (String) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT status FROM outbox WHERE org_id = ? AND tipo = 'EVENTO_CALENDARIO'")
                .setParameter(1, org.valor()).getSingleResult());
    }

    private long quantosCompromissos(OrgId org) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT count(*) FROM outbox WHERE org_id = ? AND tipo = 'EVENTO_CALENDARIO'")
                .setParameter(1, org.valor()).getSingleResult())).longValue();
    }

    private UUID lembreteDe(OrgId org, UUID origem) {
        return (UUID) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT id FROM pendencia WHERE org_id = ? AND origem_pendencia_id = ?")
                .setParameter(1, org.valor()).setParameter(2, origem).getSingleResult());
    }

    private String eventoIdDe(OrgId org, UUID lembreteId) {
        return (String) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT evento_calendario_id FROM pendencia WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, lembreteId).getSingleResult());
    }

    private List<String> tipos(OrgId org, UUID pendenciaId) {
        return QuarkusTransaction.requiringNew().call(() ->
                trilha.daPendencia(org, pendenciaId).stream().map(EventoRegistrado::tipo).toList());
    }
}
