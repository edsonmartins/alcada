package app.alcada.captura;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import app.alcada.notificacao.internal.LinktorStub;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.internal.WorkerOutbox;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** C6 — ativar um grupo publica o aviso (bot visível) e só então habilita a captura. */
@QuarkusTest
class C6AvisoGrupoTest {

    @Inject
    WorkerOutbox worker;
    @Inject
    LinktorStub linktor;
    @Inject
    EntityManager em;

    @Test
    void ativar_publica_o_aviso_no_grupo_e_marca_aviso_em() {
        OrgId org = novaOrg();
        String canal = "CH-C6-" + UUID.randomUUID();
        criarFonte(org, canal);
        String g = "120363555555555555@g.us";
        descobrir(org, canal, g); // o bot já viu o grupo (inativo)

        // O gestor ativa com finalidade → enfileira o aviso (bot visível).
        given().header("X-Org-Id", org.valor().toString())
                .contentType("application/json")
                .body("{\"ativa\":true,\"finalidade\":\"acompanhar decisões do projeto\"}")
        .when().put("/v1/grupos/" + g)
        .then().statusCode(204);

        assertEquals(1L, contar(org,
                "SELECT count(*) FROM outbox WHERE org_id = ? AND tipo = 'grupo.aviso'"),
                "ativar enfileira o aviso");
        assertEquals(0L, contar(org,
                "SELECT count(*) FROM grupo_acompanhado WHERE org_id = ? AND grupo_id = '" + g
                        + "' AND aviso_em IS NOT NULL"),
                "antes da entrega, o aviso ainda não foi publicado");

        // O worker do outbox entrega ao canal (stub) → marca aviso_em.
        worker.processarLote();

        assertEquals(1, linktor.avisos().stream()
                .filter(a -> a.grupoId().equals(g)).count(),
                "o aviso foi publicado no grupo pelo canal");
        assertTrue(linktor.avisos().stream().anyMatch(a -> a.grupoId().equals(g)
                        && a.channelId().equals(canal) && a.texto().contains("assistente Alçada")),
                "aviso endereça o grupo pelo canal, com texto de transparência");
        assertEquals(1L, contar(org,
                "SELECT count(*) FROM grupo_acompanhado WHERE org_id = ? AND grupo_id = '" + g
                        + "' AND aviso_em IS NOT NULL"),
                "publicado o aviso, a captura do grupo passa a ser permitida (C6)");
    }

    // ---- helpers -----------------------------------------------------------

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    private void criarFonte(OrgId org, String canal) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO fonte (id, org_id, tipo, identificador, segredo, linktor_channel_id)
                VALUES (?, ?, 'WHATSAPP', 'grupo', 's', ?)
                """).setParameter(1, UUID.randomUUID()).setParameter(2, org.valor())
                .setParameter(3, canal).executeUpdate());
    }

    private void descobrir(OrgId org, String canal, String grupoId) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO grupo_acompanhado (org_id, fonte_id, grupo_id, nome, ativa)
                SELECT ?, id, ?, 'Projeto', false FROM fonte WHERE linktor_channel_id = ?
                """).setParameter(1, org.valor()).setParameter(2, grupoId)
                .setParameter(3, canal).executeUpdate());
    }

    private long contar(OrgId org, String sql) {
        return ((Number) QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery(sql).setParameter(1, org.valor()).getSingleResult())).longValue();
    }
}
