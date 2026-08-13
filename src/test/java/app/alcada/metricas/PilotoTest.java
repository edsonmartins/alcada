package app.alcada.metricas;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** Autorização, isolamento e semântica básica do relatório 028. */
@QuarkusTest
class PilotoTest {
    @Inject EntityManager em;

    @Test
    void acesso_sem_admin_e_negado() {
        Ctx c=novo();
        given().header("X-Org-Id",c.org).header("X-Pessoa-Id",c.pessoa)
                .queryParam("inicio",OffsetDateTime.now().minusDays(1).toString())
                .queryParam("fim",OffsetDateTime.now().plusDays(1).toString())
        .when().get("/v1/piloto/relatorio").then().statusCode(403);
    }

    @Test
    void relatorio_admin_explica_que_escape_nao_e_recall() {
        Ctx c=novo();
        given().header("X-Org-Id",c.org).header("X-Pessoa-Id",c.pessoa).header("X-Alcada-Papel","ADMIN")
                .queryParam("inicio",OffsetDateTime.now().minusDays(1).toString())
                .queryParam("fim",OffsetDateTime.now().plusDays(1).toString())
        .when().get("/v1/piloto/relatorio").then().statusCode(200)
                .body("captura.aviso",containsString("não é recall exato"))
                .body("n2.porAusencia",equalTo(0));
    }

    @Test
    void reconciliacao_de_outro_tenant_nao_vaza() {
        Ctx a=novo(), b=novo();
        given().header("X-Org-Id",b.org).header("X-Pessoa-Id",b.pessoa).header("X-Alcada-Papel","ADMIN")
                .contentType("application/json").body("{\"semana\":\"2026-08-10\",\"decisoesForaDaFila\":7}")
                .post("/v1/piloto/reconciliacoes").then().statusCode(201);
        given().header("X-Org-Id",a.org).header("X-Pessoa-Id",a.pessoa).header("X-Alcada-Papel","ADMIN")
                .queryParam("inicio","2026-08-01T00:00:00Z").queryParam("fim","2026-09-01T00:00:00Z")
                .get("/v1/piloto/relatorio").then().statusCode(200).body("captura.decisoesForaDaFila",equalTo(0));
    }

    private Ctx novo(){String org=UUID.randomUUID().toString(),p=UUID.randomUUID().toString();
        QuarkusTransaction.requiringNew().run(() -> {em.createNativeQuery("INSERT INTO organizacao(id,nome,sku) VALUES (?,'Org','CLOUD')")
                .setParameter(1,UUID.fromString(org)).executeUpdate(); em.createNativeQuery("INSERT INTO pessoa(id,org_id,nome) VALUES (?,?,'Admin')")
                .setParameter(1,UUID.fromString(p)).setParameter(2,UUID.fromString(org)).executeUpdate();}); return new Ctx(org,p);}
    private record Ctx(String org,String pessoa){}
}
