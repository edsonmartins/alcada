package app.alcada.autonomia;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.UUID;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** Cenários centrais do pacote 029. */
@QuarkusTest
class DestinosRepasseTest {
    @Inject EntityManager em;

    @Test
    void busca_unifica_equipe_e_contato_sem_expor_endereco() {
        Ctx c = novo();
        inserirPessoa(c.org, c.alvo, "José da Equipe");
        criarContato(c, "José Externo", "jose@example.com");

        given().header("X-Org-Id", c.org).header("X-Pessoa-Id", c.gestor)
                .queryParam("busca", "jose")
        .when().get("/v1/destinos-repasse")
        .then().statusCode(200).body("size()", equalTo(2))
                .body("tipo", hasItems("INTERNO", "EXTERNO"))
                .body("detalhe", everyItem(not(containsString("jose@example.com"))));
    }

    @Test
    void busca_nao_cruza_organizacao() {
        Ctx a = novo(); Ctx b = novo();
        inserirPessoa(b.org, b.alvo, "Pessoa Secreta");
        given().header("X-Org-Id", a.org).header("X-Pessoa-Id", a.gestor)
                .queryParam("busca", "secreta")
        .when().get("/v1/destinos-repasse")
        .then().statusCode(200).body("size()", equalTo(0));
    }

    @Test
    void contato_equivalente_e_reutilizado() {
        Ctx c=novo();
        String a=criarContato(c,"Primeiro","Pessoa@Example.com ");
        String b=criarContato(c,"Mesmo","pessoa@example.com");
        org.junit.jupiter.api.Assertions.assertEquals(a,b);
    }

    private Ctx novo(){
        String org=UUID.randomUUID().toString(), gestor=UUID.randomUUID().toString(), alvo=UUID.randomUUID().toString();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("INSERT INTO organizacao(id,nome,sku) VALUES (?,'Org','CLOUD')")
                    .setParameter(1,UUID.fromString(org)).executeUpdate();
            inserirPessoaTx(org,gestor,"Gestor");
        });
        return new Ctx(org,gestor,alvo);
    }
    private void inserirPessoa(String org,String id,String nome){QuarkusTransaction.requiringNew().run(() -> inserirPessoaTx(org,id,nome));}
    private void inserirPessoaTx(String org,String id,String nome){em.createNativeQuery("INSERT INTO pessoa(id,org_id,nome) VALUES (?,?,?)")
            .setParameter(1,UUID.fromString(id)).setParameter(2,UUID.fromString(org)).setParameter(3,nome).executeUpdate();}
    private String criarContato(Ctx c,String nome,String endereco){return given().header("X-Org-Id",c.org).header("X-Pessoa-Id",c.gestor)
            .contentType("application/json").body("{\"nome\":\""+nome+"\",\"canal\":\"EMAIL\",\"endereco\":\""+endereco+"\"}")
            .when().post("/v1/contatos").then().statusCode(201).extract().path("id");}
    private record Ctx(String org,String gestor,String alvo){}
}
