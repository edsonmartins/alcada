package app.alcada.autonomia;

import static io.restassured.RestAssured.given;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/** Endpoint de contatos externos de repasse (RFC-0008 F1.4a). */
@QuarkusTest
class ContatosResourceTest {

    @Test
    void cria_e_lista_contato_externo() {
        String org = UUID.randomUUID().toString();
        String gestor = UUID.randomUUID().toString();

        String id = given()
                .header("X-Org-Id", org).header("X-Pessoa-Id", gestor)
                .contentType("application/json")
                .body("{\"nome\":\"Clécia\",\"canal\":\"WHATSAPP\",\"endereco\":\"+5521999990000\"}")
        .when()
                .post("/v1/contatos")
        .then()
                .statusCode(201)
                .body("id", Matchers.notNullValue())
                .extract().path("id");

        given()
                .header("X-Org-Id", org).header("X-Pessoa-Id", gestor)
        .when()
                .get("/v1/contatos")
        .then()
                .statusCode(200)
                .body("find { it.id == '" + id + "' }.nome", Matchers.equalTo("Clécia"))
                .body("find { it.id == '" + id + "' }.canal", Matchers.equalTo("WHATSAPP"));
    }

    @Test
    void canal_invalido_recusado() {
        given()
                .header("X-Org-Id", UUID.randomUUID().toString())
                .header("X-Pessoa-Id", UUID.randomUUID().toString())
                .contentType("application/json")
                .body("{\"nome\":\"X\",\"canal\":\"TELEGRAM\",\"endereco\":\"@x\"}")
        .when()
                .post("/v1/contatos")
        .then()
                .statusCode(422)
                .contentType("application/problem+json")
                .body("type", Matchers.containsString("contato.invalido"));
    }

    @Test
    void sem_nome_recusado() {
        given()
                .header("X-Org-Id", UUID.randomUUID().toString())
                .header("X-Pessoa-Id", UUID.randomUUID().toString())
                .contentType("application/json")
                .body("{\"canal\":\"EMAIL\",\"endereco\":\"a@b.com\"}")
        .when()
                .post("/v1/contatos")
        .then()
                .statusCode(400);
    }
}
