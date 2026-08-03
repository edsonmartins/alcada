package app.alcada.captura;

import static io.restassured.RestAssured.given;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Canal de saída da fonte (RFC-0008 F1.5): é por onde o aviso de repasse sai no
 * WhatsApp. Antes só dava para ver/ajustar no banco; agora a config do tenant
 * expõe e edita. Escopo por organização (INV-15).
 */
@QuarkusTest
class CanalFonteTest {

    @Test
    void define_e_le_o_canal_do_linktor_na_fonte() {
        String org = UUID.randomUUID().toString();
        String id = criarFonte(org);

        given()
                .header("X-Org-Id", org).contentType("application/json")
                .body("{\"linktorChannelId\":\"ch-rioquality\"}")
        .when()
                .put("/v1/fontes/" + id + "/canal")
        .then()
                .statusCode(204);

        given().header("X-Org-Id", org)
        .when()
                .get("/v1/fontes")
        .then()
                .statusCode(200)
                .body("find { it.id == '" + id + "' }.linktorChannelId",
                        Matchers.equalTo("ch-rioquality"));
    }

    @Test
    void canal_vazio_limpa_a_saida() {
        String org = UUID.randomUUID().toString();
        String id = criarFonte(org);
        definir(org, id, "ch-antigo");

        definir(org, id, "");

        given().header("X-Org-Id", org)
        .when()
                .get("/v1/fontes")
        .then()
                .body("find { it.id == '" + id + "' }.linktorChannelId", Matchers.nullValue());
    }

    @Test
    void fonte_de_outra_organizacao_da_404() {
        String orgA = UUID.randomUUID().toString();
        String orgB = UUID.randomUUID().toString();
        String idDeB = criarFonte(orgB);

        given()
                .header("X-Org-Id", orgA).contentType("application/json")
                .body("{\"linktorChannelId\":\"ch-invasor\"}")
        .when()
                .put("/v1/fontes/" + idDeB + "/canal")
        .then()
                .statusCode(404)
                .contentType("application/problem+json");
    }

    private static void definir(String org, String id, String canal) {
        given()
                .header("X-Org-Id", org).contentType("application/json")
                .body("{\"linktorChannelId\":\"" + canal + "\"}")
        .when()
                .put("/v1/fontes/" + id + "/canal")
        .then()
                .statusCode(204);
    }

    private static String criarFonte(String org) {
        return given()
                .header("X-Org-Id", org).contentType("application/json")
                .body("{\"tipo\":\"WHATSAPP\",\"identificador\":\"+5521999990000\"}")
        .when()
                .post("/v1/fontes")
        .then()
                .statusCode(201)
                .extract().path("id");
    }
}
