package app.alcada.captura;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** Seleção de grupos (024 F1b): listar o que o bot viu e escolher o que controlar. */
@QuarkusTest
class GruposResourceTest {

    @Inject
    EntityManager em;

    @Test
    void lista_grupos_descobertos_e_ativa_um() {
        OrgId org = novaOrg();
        UUID fonteId = criarFonte(org);
        descobrirGrupo(org, fonteId, "G-1@g.us", "Projeto X");

        // GET lista o grupo descoberto, inativo por padrão (opt-in).
        given().header("X-Org-Id", org.valor().toString())
        .when().get("/v1/grupos")
        .then().statusCode(200)
                .body("find { it.grupoId == 'G-1@g.us' }.ativa", is(false))
                .body("find { it.grupoId == 'G-1@g.us' }.nome", is("Projeto X"));

        // PUT ativa o acompanhamento (com finalidade — ADR-0011 §1).
        given().header("X-Org-Id", org.valor().toString())
                .contentType("application/json")
                .body("{\"ativa\":true,\"finalidade\":\"acompanhar decisões do projeto\"}")
        .when().put("/v1/grupos/G-1@g.us")
        .then().statusCode(204);

        assertEquals(1L, contar(org,
                "SELECT count(*) FROM grupo_acompanhado WHERE org_id = ? AND grupo_id = 'G-1@g.us' AND ativa"),
                "grupo passou a ser acompanhado");
    }

    @Test
    void put_em_grupo_nunca_visto_da_404() {
        OrgId org = novaOrg();
        given().header("X-Org-Id", org.valor().toString())
                .contentType("application/json").body("{\"ativa\":true}")
        .when().put("/v1/grupos/nunca-visto@g.us")
        .then().statusCode(404);
    }

    // ---- helpers -----------------------------------------------------------

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    private UUID criarFonte(OrgId org) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO fonte (id, org_id, tipo, identificador, segredo, linktor_channel_id)
                VALUES (?, ?, 'WHATSAPP', 'grupo', 's', ?)
                """).setParameter(1, id).setParameter(2, org.valor())
                .setParameter(3, "CH-" + id).executeUpdate());
        return id;
    }

    private void descobrirGrupo(OrgId org, UUID fonteId, String grupoId, String nome) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO grupo_acompanhado (org_id, fonte_id, grupo_id, nome)
                VALUES (?, ?, ?, ?)
                """).setParameter(1, org.valor()).setParameter(2, fonteId)
                .setParameter(3, grupoId).setParameter(4, nome).executeUpdate());
    }

    private long contar(OrgId org, String sql) {
        return ((Number) QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery(sql).setParameter(1, org.valor()).getSingleResult())).longValue();
    }
}
