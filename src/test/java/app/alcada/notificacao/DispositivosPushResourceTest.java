package app.alcada.notificacao;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** C6: registro e revogação de push são isolados por organização, pessoa e instalação. */
@QuarkusTest
class DispositivosPushResourceTest {

    @Inject EntityManager em;

    @Test
    void registra_atualiza_e_revoga_apenas_a_instalacao_autenticada() {
        UUID org = UUID.randomUUID();
        UUID pessoa = UUID.randomUUID();
        UUID outraPessoa = UUID.randomUUID();
        UUID instalacao = UUID.randomUUID();
        UUID outraInstalacao = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("INSERT INTO organizacao(id,nome,sku) VALUES (?,'Org','CLOUD')")
                    .setParameter(1, org).executeUpdate();
            em.createNativeQuery("INSERT INTO pessoa(id,org_id,nome) VALUES (?,?,'Pessoa'),(?,?,'Outra')")
                    .setParameter(1, pessoa).setParameter(2, org)
                    .setParameter(3, outraPessoa).setParameter(4, org).executeUpdate();
        });

        var headers = new io.restassured.specification.RequestSpecification[] {
                given().header("X-Org-Id", org).header("X-Pessoa-Id", pessoa)
                        .contentType("application/json")
        };
        headers[0].body("{\"instalacaoId\":\"" + instalacao + "\",\"plataforma\":\"IOS\",\"token\":\"token-1\"}")
                .when().put("/v1/dispositivos-push").then().statusCode(204);
        headers[0].body("{\"instalacaoId\":\"" + instalacao + "\",\"plataforma\":\"IOS\",\"token\":\"token-2\"}")
                .when().put("/v1/dispositivos-push").then().statusCode(204);

        assertEquals(1, count(org, pessoa, instalacao));
        assertEquals(0, count(org, pessoa, outraInstalacao));

        given().header("X-Org-Id", org).header("X-Pessoa-Id", outraPessoa)
                .when().delete("/v1/dispositivos-push/" + instalacao).then().statusCode(204);
        assertEquals(1, count(org, pessoa, instalacao), "outra pessoa não revoga o dispositivo");

        given().header("X-Org-Id", org).header("X-Pessoa-Id", pessoa)
                .when().delete("/v1/dispositivos-push/" + instalacao).then().statusCode(204);
        assertEquals(0, count(org, pessoa, instalacao));
    }

    @Test
    void rejeita_plataforma_e_instalacao_invalidas() {
        UUID org = UUID.randomUUID();
        UUID pessoa = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("INSERT INTO organizacao(id,nome,sku) VALUES (?,'Org','CLOUD')")
                    .setParameter(1, org).executeUpdate();
            em.createNativeQuery("INSERT INTO pessoa(id,org_id,nome) VALUES (?,?,'Pessoa')")
                    .setParameter(1, pessoa).setParameter(2, org).executeUpdate();
        });
        given().header("X-Org-Id", org).header("X-Pessoa-Id", pessoa)
                .contentType("application/json")
                .body("{\"instalacaoId\":\"nao-uuid\",\"plataforma\":\"WEB\",\"token\":\"t\"}")
                .when().put("/v1/dispositivos-push").then().statusCode(422);
    }

    private int count(UUID org, UUID pessoa, UUID instalacao) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT count(*) FROM dispositivo_push WHERE org_id=? AND pessoa_id=? AND instalacao_id=?")
                .setParameter(1, org).setParameter(2, pessoa).setParameter(3, instalacao)
                .getSingleResult())).intValue();
    }
}
