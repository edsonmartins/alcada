package app.alcada.notificacao;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** C1: o link canônico conduz ao detalhe autenticado; formato inválido não vaza estado. */
@QuarkusTest
class LinkAplicativoResourceTest {

    @Test
    void c1_link_valido_redireciona_para_o_executor_com_o_alvo() {
        UUID delegacao = UUID.randomUUID();
        given().redirects().follow(false)
                .when().get("/app/delegacoes/" + delegacao)
                .then().statusCode(303)
                .header("Location", containsString("/executor?delegacaoId=" + delegacao));
    }

    @Test
    void c2_id_invalido_nao_entrega_conteudo() {
        given().when().get("/app/delegacoes/not-a-uuid")
                .then().statusCode(404)
                .header("Location", org.hamcrest.Matchers.nullValue());
    }
}
