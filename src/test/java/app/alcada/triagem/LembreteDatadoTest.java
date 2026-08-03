package app.alcada.triagem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoRegistrado;
import app.alcada.triagem.internal.TriagemService;
import app.alcada.triagem.port.Triagem;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/**
 * Lembrete datado (RFC-0009, fatia F2.1): o RESOLVER fecha o item e o compromisso
 * que sobra vira uma pendência dormindo, que volta pela Entrada no dia — fila
 * única (INV-03), sem caixa de lembretes (ADR-0018).
 */
@QuarkusTest
class LembreteDatadoTest {

    @Inject TriagemService triagem;
    @Inject ConsultaTrilha trilha;
    @Inject EntityManager em;

    // C1 — resolver com lembrete fecha o item e agenda o compromisso
    @Test
    void resolver_com_lembrete_fecha_o_item_e_cria_o_lembrete_dormindo() {
        Ctx c = novo();
        OffsetDateTime quinta = agora().plusDays(3);

        triagem.resolver(c.org, c.pend, "reunião marcada",
                new Triagem.Lembrete(quinta, "Reunião Sharpi"), c.gestor);

        assertEquals("FECHADA", status(c.org, c.pend), "o item resolvido sai da fila");
        Object[] l = lembreteDe(c.org, c.pend);
        assertNotNull(l, "o compromisso virou um item");
        assertEquals("Reunião Sharpi", l[1]);
        assertEquals("DORMINDO", l[2], "dorme até a data — invisível na Entrada");
        assertEquals("LEMBRETE", l[3]);
        assertEquals(quinta.toInstant(), (java.time.Instant) l[4], "desperta na data marcada");
    }

    // C2 — a trilha liga origem → lembrete (INV-11)
    @Test
    void trilha_registra_resolvida_e_lembrete_criado_na_origem() {
        Ctx c = novo();
        triagem.resolver(c.org, c.pend, null,
                new Triagem.Lembrete(agora().plusDays(2), "Reunião Sharpi"), c.gestor);

        List<EventoRegistrado> eventos = eventos(c.org, c.pend);
        assertTrue(eventos.stream().anyMatch(e -> e.tipo().equals("RESOLVIDA")));
        EventoRegistrado criado = eventos.stream()
                .filter(e -> e.tipo().equals("LEMBRETE_CRIADO")).findFirst().orElseThrow();
        String lembreteId = lembreteDe(c.org, c.pend)[0].toString();
        assertTrue(carga(c.org, c.pend).contains(lembreteId), "o evento aponta para o lembrete");
        assertTrue(criado.ator().startsWith("HUMANO:"), "quem decidiu foi o gestor");
    }

    // C3 — no dia, o lembrete volta pela Entrada (fila única)
    @Test
    void lembrete_desperta_na_entrada_no_dia() {
        Ctx c = novo();
        triagem.resolver(c.org, c.pend, null,
                new Triagem.Lembrete(agora().plusDays(1), "Reunião Sharpi"), c.gestor);
        UUID lembreteId = (UUID) lembreteDe(c.org, c.pend)[0];

        triagem.aoDespertar(c.org, lembreteId, 1);

        assertEquals("ENTRADA", status(c.org, lembreteId), "no dia, aparece na fila de sempre");
        assertTrue(eventos(c.org, lembreteId).stream().anyMatch(e -> e.tipo().equals("DESPERTADA")));
    }

    // C4 — regressão: resolver sem lembrete continua igual
    @Test
    void resolver_sem_lembrete_nao_cria_nada() {
        Ctx c = novo();
        triagem.resolver(c.org, c.pend, "feito", c.gestor);

        assertEquals("FECHADA", status(c.org, c.pend));
        assertEquals(0L, quantosLembretes(c.org), "nenhum item novo");
    }

    // C5 — lembrete no passado é recusado, e o item NÃO fecha meio resolvido
    @Test
    void lembrete_no_passado_recusa_e_nao_resolve() {
        Ctx c = novo();
        var lembrete = new Triagem.Lembrete(agora().minusDays(1), "Reunião que já passou");

        assertThrows(RuntimeException.class,
                () -> triagem.resolver(c.org, c.pend, null, lembrete, c.gestor));

        assertEquals("ENTRADA", status(c.org, c.pend), "o item continua na fila");
        assertEquals(0L, quantosLembretes(c.org));
    }

    // C5b — data absurda quase sempre é data mal interpretada (INV-10)
    @Test
    void lembrete_a_mais_de_doze_meses_recusa() {
        Ctx c = novo();
        var lembrete = new Triagem.Lembrete(agora().plusMonths(13), "Reunião em 2027");

        assertThrows(RuntimeException.class,
                () -> triagem.resolver(c.org, c.pend, null, lembrete, c.gestor));
        assertEquals("ENTRADA", status(c.org, c.pend));
    }

    // C6 — o horizonte sai da distância até a data (ADR-0008)
    @Test
    void horizonte_deriva_da_data_do_compromisso() {
        assertEquals("HOJE", horizonteDeLembrete(agora().plusHours(3)));
        assertEquals("SEMANA", horizonteDeLembrete(agora().plusDays(3)));
        assertEquals("TRIMESTRE", horizonteDeLembrete(agora().plusDays(40)));
    }

    // C7 — o lembrete não é captura: não conta como "entrou" no encolhimento (INV-01)
    @Test
    void lembrete_nao_conta_como_item_captado() {
        Ctx c = novo();
        triagem.resolver(c.org, c.pend, null,
                new Triagem.Lembrete(agora().plusDays(2), "Reunião Sharpi"), c.gestor);

        assertEquals(0L, contaEventos(c.org, "CAPTADA"),
                "lembrete não emite CAPTADA — o encolhimento não infla");
    }

    // C8 — isolamento por organização (INV-15)
    @Test
    void lembrete_fica_na_organizacao_do_item() {
        Ctx a = novo();
        Ctx b = novo();
        triagem.resolver(a.org, a.pend, null,
                new Triagem.Lembrete(agora().plusDays(2), "Reunião Sharpi"), a.gestor);

        assertEquals(1L, quantosLembretes(a.org));
        assertEquals(0L, quantosLembretes(b.org), "B não enxerga o lembrete de A");
    }

    // C9 — pelo endpoint: resolver com lembrete no corpo, e a recusa em problem+json
    @Test
    void endpoint_resolve_com_lembrete_e_recusa_data_passada() {
        Ctx c = novo();
        String org = c.org.valor().toString();
        String gestor = c.gestor.toString();

        io.restassured.RestAssured.given()
                .header("X-Org-Id", org).header("X-Pessoa-Id", gestor)
                .contentType("application/json")
                .body("{\"nota\":\"reunião marcada\",\"lembrete\":{\"quando\":\""
                        + agora().plusDays(3) + "\",\"texto\":\"Reunião Sharpi\"}}")
        .when()
                .post("/v1/pendencias/" + c.pend + "/resolver")
        .then()
                .statusCode(204);

        assertEquals("Reunião Sharpi", lembreteDe(c.org, c.pend)[1]);

        Ctx outro = novo();
        io.restassured.RestAssured.given()
                .header("X-Org-Id", outro.org.valor().toString())
                .header("X-Pessoa-Id", outro.gestor.toString())
                .contentType("application/json")
                .body("{\"lembrete\":{\"quando\":\"" + agora().minusDays(1) + "\",\"texto\":\"Ontem\"}}")
        .when()
                .post("/v1/pendencias/" + outro.pend + "/resolver")
        .then()
                .statusCode(422)
                .contentType("application/problem+json");
        assertEquals("ENTRADA", status(outro.org, outro.pend), "recusado, o item não fecha");
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

    private String horizonteDeLembrete(OffsetDateTime quando) {
        Ctx c = novo();
        triagem.resolver(c.org, c.pend, null, new Triagem.Lembrete(quando, "Compromisso"), c.gestor);
        return (String) lembreteDe(c.org, c.pend)[5];
    }

    private static OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    /** [id, titulo, status, origem, volta_em, horizonte] do lembrete da origem. */
    private Object[] lembreteDe(OrgId org, UUID origem) {
        return (Object[]) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery("""
                SELECT id, titulo, status, origem, volta_em, horizonte FROM pendencia
                WHERE org_id = ? AND origem_pendencia_id = ?
                """).setParameter(1, org.valor()).setParameter(2, origem).getSingleResult());
    }

    private long quantosLembretes(OrgId org) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT count(*) FROM pendencia WHERE org_id = ? AND origem = 'LEMBRETE'")
                .setParameter(1, org.valor()).getSingleResult())).longValue();
    }

    private long contaEventos(OrgId org, String tipo) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT count(*) FROM trilha WHERE org_id = ? AND tipo = ?")
                .setParameter(1, org.valor()).setParameter(2, tipo).getSingleResult())).longValue();
    }

    private String carga(OrgId org, UUID pendenciaId) {
        return (String) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery("""
                SELECT carga::text FROM trilha
                WHERE org_id = ? AND pendencia_id = ? AND tipo = 'LEMBRETE_CRIADO'
                """).setParameter(1, org.valor()).setParameter(2, pendenciaId).getSingleResult());
    }

    private String status(OrgId org, UUID pendenciaId) {
        return (String) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT status FROM pendencia WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, pendenciaId).getSingleResult());
    }

    private List<EventoRegistrado> eventos(OrgId org, UUID pendenciaId) {
        return QuarkusTransaction.requiringNew().call(() -> trilha.daPendencia(org, pendenciaId));
    }
}
