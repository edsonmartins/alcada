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

    // F1.5 — o telefone muda; o contato é o mesmo (as delegações seguem apontando)
    @Test
    void edita_nome_canal_e_endereco_do_contato() {
        String org = UUID.randomUUID().toString();
        String gestor = UUID.randomUUID().toString();
        String id = criar(org, gestor, "{\"nome\":\"Marcello\",\"canal\":\"WHATSAPP\","
                + "\"endereco\":\"+5521988887777\"}");

        given()
                .header("X-Org-Id", org).header("X-Pessoa-Id", gestor)
                .contentType("application/json")
                .body("{\"nome\":\"Marcello Andrade\",\"canal\":\"EMAIL\","
                        + "\"endereco\":\"marcello@rioquality.com.br\"}")
        .when()
                .put("/v1/contatos/" + id)
        .then()
                .statusCode(204);

        given()
                .header("X-Org-Id", org).header("X-Pessoa-Id", gestor)
        .when()
                .get("/v1/contatos")
        .then()
                .statusCode(200)
                .body("find { it.id == '" + id + "' }.nome", Matchers.equalTo("Marcello Andrade"))
                .body("find { it.id == '" + id + "' }.canal", Matchers.equalTo("EMAIL"))
                .body("find { it.id == '" + id + "' }.endereco",
                        Matchers.equalTo("marcello@rioquality.com.br"));
    }

    // INV-15 — contato de outro tenant não existe aqui (nem para editar)
    @Test
    void editar_contato_de_outra_organizacao_da_404() {
        String orgA = UUID.randomUUID().toString();
        String orgB = UUID.randomUUID().toString();
        String gestor = UUID.randomUUID().toString();
        String idDeB = criar(orgB, gestor,
                "{\"nome\":\"Paulo\",\"canal\":\"EMAIL\",\"endereco\":\"paulo@rioquality.com.br\"}");

        given()
                .header("X-Org-Id", orgA).header("X-Pessoa-Id", gestor)
                .contentType("application/json")
                .body("{\"nome\":\"Invasor\",\"canal\":\"EMAIL\",\"endereco\":\"x@y.com\"}")
        .when()
                .put("/v1/contatos/" + idDeB)
        .then()
                .statusCode(404)
                .contentType("application/problem+json");
    }

    @Test
    void editar_com_canal_invalido_recusado() {
        String org = UUID.randomUUID().toString();
        String gestor = UUID.randomUUID().toString();
        String id = criar(org, gestor,
                "{\"nome\":\"Clécia\",\"canal\":\"WHATSAPP\",\"endereco\":\"+5521999990000\"}");

        given()
                .header("X-Org-Id", org).header("X-Pessoa-Id", gestor)
                .contentType("application/json")
                .body("{\"nome\":\"Clécia\",\"canal\":\"TELEGRAM\",\"endereco\":\"@c\"}")
        .when()
                .put("/v1/contatos/" + id)
        .then()
                .statusCode(422)
                .body("type", Matchers.containsString("contato.invalido"));
    }

    private static String criar(String org, String gestor, String corpo) {
        return given()
                .header("X-Org-Id", org).header("X-Pessoa-Id", gestor)
                .contentType("application/json").body(corpo)
        .when()
                .post("/v1/contatos")
        .then()
                .statusCode(201)
                .extract().path("id");
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
