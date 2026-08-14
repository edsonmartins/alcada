package app.alcada.identidade;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.UUID;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CanaisPessoaResourceTest {
    @Inject EntityManager em;

    @Test
    void pessoa_salva_e_remove_apenas_o_proprio_whatsapp() {
        UUID org = UUID.randomUUID();
        UUID pessoa = UUID.randomUUID();
        UUID outra = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("INSERT INTO organizacao(id,nome,sku) VALUES (?,'Org','CLOUD')")
                    .setParameter(1, org).executeUpdate();
            em.createNativeQuery("INSERT INTO pessoa(id,org_id,nome) VALUES (?,?,'Pessoa'),(?,?,'Outra')")
                    .setParameter(1, pessoa).setParameter(2, org)
                    .setParameter(3, outra).setParameter(4, org).executeUpdate();
        });

        given().header("X-Org-Id", org).header("X-Pessoa-Id", pessoa)
                .contentType("application/json").body("{\"whatsapp\":\"+5544999990000\"}")
                .when().put("/v1/perfil/canais").then().statusCode(204);
        given().header("X-Org-Id", org).header("X-Pessoa-Id", pessoa)
                .when().get("/v1/perfil/canais").then().statusCode(200)
                .body("whatsapp", equalTo("+5544999990000"));
        given().header("X-Org-Id", org).header("X-Pessoa-Id", outra)
                .when().get("/v1/perfil/canais").then().statusCode(200)
                .body("whatsapp", equalTo(null));
    }

    @Test
    void recusa_whatsapp_fora_de_e164() {
        UUID org = UUID.randomUUID();
        UUID pessoa = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("INSERT INTO organizacao(id,nome,sku) VALUES (?,'Org','CLOUD')")
                    .setParameter(1, org).executeUpdate();
            em.createNativeQuery("INSERT INTO pessoa(id,org_id,nome) VALUES (?,?,'Pessoa')")
                    .setParameter(1, pessoa).setParameter(2, org).executeUpdate();
        });
        given().header("X-Org-Id", org).header("X-Pessoa-Id", pessoa)
                .contentType("application/json").body("{\"whatsapp\":\"44999990000\"}")
                .when().put("/v1/perfil/canais").then().statusCode(422);
    }
}
