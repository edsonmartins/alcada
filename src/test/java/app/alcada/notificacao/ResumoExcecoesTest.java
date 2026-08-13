package app.alcada.notificacao;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import app.alcada.notificacao.internal.LinktorStub;
import app.alcada.notificacao.internal.ResumosExcecao;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.internal.WorkerOutbox;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ResumoExcecoesTest {
    @Inject EntityManager em;@Inject ResumosExcecao resumos;@Inject WorkerOutbox worker;@Inject LinktorStub linktor;
    @BeforeEach void limpar(){linktor.limpar();}

    @Test void preferencia_e_por_gestor_e_agenda_periodos(){Ctx c=novo(true);
        given().headers("X-Org-Id",c.org.valor(),"X-Pessoa-Id",c.gestor).contentType("application/json")
                .body("{\"canal\":\"EMAIL\",\"resumoInicio\":\"09:00\",\"resumoFim\":\"17:00\",\"ativa\":true}")
                .put("/v1/preferencias-notificacao").then().statusCode(204);
        assertEquals(2L,contar(c.org,"SELECT count(*) FROM job WHERE org_id=? AND tipo='RESUMO_EXCECOES'"));
        given().headers("X-Org-Id",c.org.valor(),"X-Pessoa-Id",c.gestor).get("/v1/preferencias-notificacao")
                .then().statusCode(200).body("canal",org.hamcrest.Matchers.equalTo("EMAIL"));}

    @Test void resumo_vazio_e_silencio(){Ctx c=novo(true);preferir(c);resumos.executar(c.org,c.gestor,"INICIO");
        assertEquals(0L,contar(c.org,"SELECT count(*) FROM outbox WHERE org_id=? AND tipo='RESUMO_EXCECOES'"));}

    @Test void resumo_agrega_excecoes_deduplica_e_entrega_via_linktor_email(){Ctx c=novo(true);preferir(c);criarExcecao(c);
        resumos.executar(c.org,c.gestor,"INICIO");resumos.executar(c.org,c.gestor,"INICIO");
        assertEquals(1L,contar(c.org,"SELECT count(*) FROM outbox WHERE org_id=? AND tipo='RESUMO_EXCECOES'"));
        worker.processarLote();
        var envios=linktor.diretas().stream().filter(d->"gestor@example.com".equals(d.to())).toList();
        assertEquals(1,envios.size());assertTrue(envios.getFirst().texto().contains("Ação: despachar no Alçada"));}

    @Test void sem_canal_email_resolvido_nao_inventa_destino(){Ctx c=novo(false);preferir(c);criarExcecao(c);resumos.executar(c.org,c.gestor,"FIM");worker.processarLote();assertTrue(linktor.diretas().isEmpty());}

    @Test void c16_payload_textual_legado_continua_entregavel(){Ctx c=novo(true);QuarkusTransaction.requiringNew().run(()->em.createNativeQuery("INSERT INTO outbox(org_id,tipo,payload,idempotency_key) VALUES (?,'RESUMO_EXCECOES',cast(? as jsonb),?)").setParameter(1,c.org.valor()).setParameter(2,"{\"gestor_id\":\""+c.gestor+"\",\"periodo\":\"INICIO\",\"nota\":\"Resumo legado acionável\"}").setParameter(3,"legado:"+c.gestor).executeUpdate());worker.processarLote();assertEquals(1,linktor.diretas().stream().filter(d->d.texto().contains("Resumo legado")).count());}

    private void preferir(Ctx c){given().headers("X-Org-Id",c.org.valor(),"X-Pessoa-Id",c.gestor).contentType("application/json")
            .body("{\"canal\":\"EMAIL\",\"resumoInicio\":\"09:00\",\"resumoFim\":null,\"ativa\":true}").put("/v1/preferencias-notificacao").then().statusCode(204);}
    private void criarExcecao(Ctx c){QuarkusTransaction.requiringNew().run(()->{UUID p=UUID.randomUUID(),d=UUID.randomUUID();
        em.createNativeQuery("INSERT INTO pendencia(id,org_id,titulo,classe,horizonte,status) VALUES (?,?,'Exceção','DECISAO','HOJE','DELEGADA')").setParameter(1,p).setParameter(2,c.org.valor()).executeUpdate();
        em.createNativeQuery("INSERT INTO delegacao(id,org_id,pendencia_id,dono_id,nivel,prazo,gestor_id,janela,escalonamento,status) VALUES (?,?,?,?, 'N2',?,?,interval '4 hours',interval '1 day','AGUARDANDO_JANELA')")
                .setParameter(1,d).setParameter(2,c.org.valor()).setParameter(3,p).setParameter(4,UUID.randomUUID()).setParameter(5,OffsetDateTime.now().plusHours(2)).setParameter(6,c.gestor).executeUpdate();
        em.createNativeQuery("INSERT INTO job(org_id,tipo,chave,payload,executar_em) VALUES (?,'AUT_VIRADA',?,cast(? as jsonb),?)")
                .setParameter(1,c.org.valor()).setParameter(2,d.toString()).setParameter(3,"{\"delegacao_id\":\""+d+"\"}").setParameter(4,OffsetDateTime.now().plusHours(2)).executeUpdate();});}
    private Ctx novo(boolean canal){OrgId o=new OrgId(UUID.randomUUID());UUID g=UUID.randomUUID();QuarkusTransaction.requiringNew().run(()->{em.createNativeQuery("INSERT INTO organizacao(id,nome,timezone) VALUES (?,'Org','America/Sao_Paulo')").setParameter(1,o.valor()).executeUpdate();
        em.createNativeQuery("INSERT INTO pessoa(id,org_id,nome,email) VALUES (?,?,'Gestor','gestor@example.com')").setParameter(1,g).setParameter(2,o.valor()).executeUpdate();
        if(canal)em.createNativeQuery("INSERT INTO fonte(id,org_id,tipo,identificador,segredo,linktor_channel_id) VALUES (?,?,'EMAIL','email','seg','email-channel')").setParameter(1,UUID.randomUUID()).setParameter(2,o.valor()).executeUpdate();});return new Ctx(o,g);}
    private long contar(OrgId o,String sql){return ((Number)QuarkusTransaction.requiringNew().call(()->em.createNativeQuery(sql).setParameter(1,o.valor()).getSingleResult())).longValue();}
    private record Ctx(OrgId org,UUID gestor){}
}
