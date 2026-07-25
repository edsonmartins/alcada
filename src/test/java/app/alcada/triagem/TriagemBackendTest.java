package app.alcada.triagem;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.scheduler.internal.WorkerJobs;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoRegistrado;
import app.alcada.triagem.internal.TriagemService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** Cenários backend do 003 (as saídas + adiar + hoje + despertar). */
@QuarkusTest
class TriagemBackendTest {

    @Inject TriagemService triagem;
    @Inject WorkerJobs worker;
    @Inject ConsultaTrilha trilha;
    @Inject EntityManager em;

    @Test
    void resolver_fecha_o_item_e_avisa_o_solicitante() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID pend = novaPendencia(org, null);
        UUID gestor = UUID.randomUUID();

        triagem.resolver(org, pend, "feito", gestor);

        assertEquals("FECHADA", status(org, pend));
        assertTrue(tipos(org, pend).contains("RESOLVIDA"));
        assertEquals(1L, countOutbox(org, "item.fechado"));
    }

    @Test
    void reservar_agenda_e_continua_dependente() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID pend = novaPendencia(org, null);

        triagem.reservar(org, pend, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), UUID.randomUUID());

        assertEquals("AGENDADA", status(org, pend));
        assertTrue(tipos(org, pend).contains("RESERVADA"));
    }

    @Test
    void repousar_adormece_e_desperta_na_data() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID pend = novaPendencia(org, null);

        triagem.repousar(org, pend, OffsetDateTime.now(ZoneOffset.UTC), UUID.randomUUID());
        assertEquals("DORMINDO", status(org, pend));
        assertTrue(tipos(org, pend).contains("REPOUSADA"));

        worker.processarDevidos();  // despertar (volta_em = agora → devido)
        worker.processarDevidos();  // idempotente: não desperta de novo

        assertEquals("ENTRADA", status(org, pend));
        long despertadas = tipos(org, pend).stream().filter("DESPERTADA"::equals).count();
        assertEquals(1L, despertadas, "desperta exatamente uma vez");
    }

    @Test
    void adiar_responde_diferente_a_cada_motivo() {
        OrgId org = new OrgId(UUID.randomUUID());
        OffsetDateTime amanha = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);

        assertEquals("bloco_decisao", triagem.adiar(org, novaPendencia(org, null), amanha, "NADA", UUID.randomUUID()));
        assertEquals("repassar", triagem.adiar(org, novaPendencia(org, null), amanha, "TERCEIRO", UUID.randomUUID()));
        assertEquals("cobrar_insumo", triagem.adiar(org, novaPendencia(org, null), amanha, "INSUMO", UUID.randomUUID()));
    }

    @Test
    void adiar_exige_data_e_motivo_valido() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID pend = novaPendencia(org, null);
        String amanha = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString();
        String hdrOrg = org.valor().toString();
        String hdrPessoa = UUID.randomUUID().toString();

        // sem volta_em → 400
        given().header("X-Org-Id", hdrOrg).header("X-Pessoa-Id", hdrPessoa).contentType("application/json")
                .body("{\"oQueFalta\":\"NADA\"}")
        .when().post("/v1/pendencias/" + pend + "/adiar")
        .then().statusCode(400);

        // motivo inválido → 422
        given().header("X-Org-Id", hdrOrg).header("X-Pessoa-Id", hdrPessoa).contentType("application/json")
                .body("{\"voltaEm\":\"" + amanha + "\",\"oQueFalta\":\"QUALQUER\"}")
        .when().post("/v1/pendencias/" + pend + "/adiar")
        .then().statusCode(422);

        // válido → 200 e adiado_count incrementado
        given().header("X-Org-Id", hdrOrg).header("X-Pessoa-Id", hdrPessoa).contentType("application/json")
                .body("{\"voltaEm\":\"" + amanha + "\",\"oQueFalta\":\"INSUMO\"}")
        .when().post("/v1/pendencias/" + pend + "/adiar")
        .then().statusCode(200);

        assertEquals(1, ((Number) unica(org,
                "SELECT adiado_count FROM pendencia WHERE org_id = ? AND id = '" + pend + "'")).intValue());
        assertTrue(tipos(org, pend).contains("ADIADA"));
    }

    @Test
    void hoje_mostra_no_maximo_tres() {
        OrgId org = new OrgId(UUID.randomUUID());
        for (int i = 0; i < 5; i++) {
            novaPendencia(org, java.math.BigDecimal.valueOf((i + 1) * 1000L));
        }
        List<TriagemService.ItemHoje> hoje = QuarkusTransaction.requiringNew().call(() -> triagem.hoje(org));
        assertEquals(3, hoje.size());
        assertTrue(hoje.stream().allMatch(h -> h.justificativa() != null && !h.justificativa().isBlank()));
    }

    // ---- helpers -----------------------------------------------------------

    private UUID novaPendencia(OrgId org, java.math.BigDecimal valor) {
        UUID pend = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org') ON CONFLICT DO NOTHING")
                    .setParameter(1, org.valor()).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status, valor_em_jogo)
                    VALUES (?, ?, 'Item', 'DECISAO', 'SEMANA', 'ENTRADA', ?)
                    """)
                    .setParameter(1, pend).setParameter(2, org.valor()).setParameter(3, valor).executeUpdate();
        });
        return pend;
    }

    private String status(OrgId org, UUID pend) {
        return (String) unica(org, "SELECT status FROM pendencia WHERE org_id = ? AND id = '" + pend + "'");
    }

    private Object unica(OrgId org, String sql) {
        return QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery(sql).setParameter(1, org.valor()).getSingleResult());
    }

    private long countOutbox(OrgId org, String tipo) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT count(*) FROM outbox WHERE org_id = ? AND tipo = ?")
                .setParameter(1, org.valor()).setParameter(2, tipo).getSingleResult())).longValue();
    }

    private List<String> tipos(OrgId org, UUID pend) {
        return QuarkusTransaction.requiringNew().call(() ->
                trilha.daPendencia(org, pend).stream().map(EventoRegistrado::tipo).toList());
    }
}
