package app.alcada.notificacao;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import app.alcada.notificacao.internal.ResumosExcecao;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ResumoDiarioExcecaoTest {
    @Inject EntityManager em;@Inject ResumosExcecao resumos;

    @Test void c1_c2_c3_c4_envelope_limita_e_reune_categorias_acionaveis(){Ctx c=novo();
        for(int i=0;i<5;i++)pend(c,"Hoje "+i,"ENTRADA");
        n2(c,"Executa logo");retorno(c,"Retorno observado");escalada(c,"Escalado");executar(c);
        String j=json(c);assertEquals(3,array(j,"hoje"));
        assertEquals(1,array(j,"n2PrestesExecutar"));
        assertEquals(1,array(j,"retornosDecisao"));
        assertEquals(1,array(j,"escalonamentos"));
        assertTrue(j.contains("http://localhost:5173/itens/"));}

    @Test void c5_c6_c7_retorno_deliberado_e_idempotente_sem_mudar_pendencia(){Ctx c=novo();UUID[] x=retorno(c,"Decidir retorno");
        Map<String,Object> h=new java.util.HashMap<>(headers(c));h.put("Idempotency-Key","ret-1");
        given().headers(h).contentType("application/json").body("{\"decisao\":\"APLICAR\"}")
                .post("/v1/retornos/"+x[1]+"/decisao").then().statusCode(204);
        given().headers(h).contentType("application/json").body("{\"decisao\":\"APLICAR\"}")
                .post("/v1/retornos/"+x[1]+"/decisao").then().statusCode(204);
        given().headers(h).contentType("application/json").body("{\"decisao\":\"REJEITAR\"}")
                .post("/v1/retornos/"+x[1]+"/decisao").then().statusCode(409);
        assertEquals("ENTRADA",texto("SELECT status FROM pendencia WHERE org_id=? AND id=?",c.org.valor(),x[0]));
        assertEquals(1,num("SELECT count(*) FROM trilha WHERE org_id=? AND tipo='RETORNO_AVALIADO'",c.org.valor()));
        executar(c);assertEquals(0,array(json(c),"retornosDecisao"));}

    @Test void c8_escalonamento_absorvido_nao_aparece(){Ctx c=novo();UUID p=escalada(c,"Absorvido");evento(c,p,"RESOLVIDA",OffsetDateTime.now(),c.gestor);QuarkusTransaction.requiringNew().run(()->em.createNativeQuery("UPDATE pendencia SET status='FECHADA' WHERE org_id=? AND id=?").setParameter(1,c.org.valor()).setParameter(2,p).executeUpdate());executar(c);assertEquals(0,array(json(c),"escalonamentos"));}

    @Test void c9_c10_estimativa_so_com_amostra_historica(){Ctx a=novo();pend(a,"Sem histórico","ENTRADA");executar(a);assertTrue(json(a).contains("\"estimativaMinutos\": null")||json(a).contains("\"estimativaMinutos\":null"));
        Ctx b=novo();UUID p=pend(b,"Com histórico","ENTRADA");OffsetDateTime base=OffsetDateTime.now().minusHours(1);for(int i=0;i<7;i++)evento(b,p,"RESOLVIDA",base.plusMinutes(i*2L),b.gestor);executar(b);assertEquals(5,arvore(json(b)).path("estimativaMinutos").asInt());}

    @Test void c11_c12_c13_periodo_deduplica_e_vazio_e_silencio(){Ctx vazio=novo();executar(vazio);executar(vazio);assertEquals(1,num("SELECT count(*) FROM resumo_diario WHERE org_id=?",vazio.org.valor()));assertEquals(0,num("SELECT count(*) FROM outbox WHERE org_id=? AND tipo='RESUMO_EXCECOES'",vazio.org.valor()));
        Ctx c=novo();pend(c,"Único","ENTRADA");try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var a=pool.submit(()->{executar(c);return 1;});var b=pool.submit(()->{executar(c);return 1;});a.get();b.get();}catch(Exception e){throw new RuntimeException(e);}assertEquals(1,num("SELECT count(*) FROM resumo_diario WHERE org_id=?",c.org.valor()));assertEquals(1,num("SELECT count(*) FROM outbox WHERE org_id=? AND tipo='RESUMO_EXCECOES'",c.org.valor()));}

    @Test void c14_tenant_e_gestor_nao_deliberam_retorno_alheio(){Ctx a=novo(),b=novo();UUID[] x=retorno(b,"Privado");given().headers(headers(a)).header("Idempotency-Key","fora").contentType("application/json").body("{\"decisao\":\"REJEITAR\"}").post("/v1/retornos/"+x[1]+"/decisao").then().statusCode(404);}

    @Test void c15_estado_muda_depois_do_retrato_sem_reenvio(){Ctx c=novo();UUID p=pend(c,"Resolvido depois","ENTRADA");executar(c);QuarkusTransaction.requiringNew().run(()->em.createNativeQuery("UPDATE pendencia SET status='FECHADA' WHERE org_id=? AND id=?").setParameter(1,c.org.valor()).setParameter(2,p).executeUpdate());executar(c);assertEquals(1,num("SELECT count(*) FROM resumo_diario WHERE org_id=?",c.org.valor()));assertEquals(1,num("SELECT count(*) FROM outbox WHERE org_id=? AND tipo='RESUMO_EXCECOES'",c.org.valor()));}

    private void executar(Ctx c){QuarkusTransaction.requiringNew().run(()->resumos.executar(c.org,c.gestor,"INICIO"));}
    private Ctx novo(){OrgId o=new OrgId(UUID.randomUUID());UUID g=UUID.randomUUID();QuarkusTransaction.requiringNew().run(()->{em.createNativeQuery("INSERT INTO organizacao(id,nome,timezone) VALUES (?,'Org','America/Sao_Paulo')").setParameter(1,o.valor()).executeUpdate();em.createNativeQuery("INSERT INTO pessoa(id,org_id,nome,email) VALUES (?,?,'Gestor','g@example.com')").setParameter(1,g).setParameter(2,o.valor()).executeUpdate();em.createNativeQuery("INSERT INTO preferencia_notificacao(org_id,gestor_id,resumo_inicio,ativa) VALUES (?,?,cast('09:00' as time),true)").setParameter(1,o.valor()).setParameter(2,g).executeUpdate();});return new Ctx(o,g);}
    private UUID pend(Ctx c,String titulo,String status){UUID p=UUID.randomUUID();QuarkusTransaction.requiringNew().run(()->em.createNativeQuery("INSERT INTO pendencia(id,org_id,titulo,classe,horizonte,status) VALUES (?,?,?,'DECISAO','HOJE',?)").setParameter(1,p).setParameter(2,c.org.valor()).setParameter(3,titulo).setParameter(4,status).executeUpdate());return p;}
    private void n2(Ctx c,String titulo){UUID p=pend(c,titulo,"DELEGADA"),d=deleg(c,p,"AGUARDANDO_JANELA");QuarkusTransaction.requiringNew().run(()->em.createNativeQuery("INSERT INTO job(org_id,tipo,chave,payload,executar_em) VALUES (?,'AUT_VIRADA',?,cast('{}' as jsonb),?)").setParameter(1,c.org.valor()).setParameter(2,d.toString()).setParameter(3,OffsetDateTime.now().plusMinutes(20)).executeUpdate());}
    private UUID[] retorno(Ctx c,String titulo){UUID p=pend(c,titulo,"ENTRADA"),d=deleg(c,p,"ABERTA"),r=UUID.randomUUID();QuarkusTransaction.requiringNew().run(()->em.createNativeQuery("INSERT INTO retorno_delegacao(id,org_id,delegacao_id,mensagem_id_hash,trecho_minimizado) VALUES (?,?,?,decode(md5(?),'hex'),'Trecho minimizado')").setParameter(1,r).setParameter(2,c.org.valor()).setParameter(3,d).setParameter(4,r.toString()).executeUpdate());return new UUID[]{p,r};}
    private UUID escalada(Ctx c,String titulo){UUID p=pend(c,titulo,"ENTRADA");deleg(c,p,"ESCALADA");evento(c,p,"ESCALADA",OffsetDateTime.now().minusMinutes(1),null);return p;}
    private UUID deleg(Ctx c,UUID p,String status){UUID d=UUID.randomUUID();QuarkusTransaction.requiringNew().run(()->em.createNativeQuery("INSERT INTO delegacao(id,org_id,pendencia_id,dono_id,nivel,prazo,janela,escalonamento,status,gestor_id) VALUES (?,?,?,?,'N2',now()+interval '1 hour',interval '4 hours',interval '1 day',?,?)").setParameter(1,d).setParameter(2,c.org.valor()).setParameter(3,p).setParameter(4,UUID.randomUUID()).setParameter(5,status).setParameter(6,c.gestor).executeUpdate());return d;}
    private void evento(Ctx c,UUID p,String tipo,OffsetDateTime quando,UUID humano){QuarkusTransaction.requiringNew().run(()->em.createNativeQuery("INSERT INTO trilha(org_id,pendencia_id,tipo,ator,ocorrido_em) VALUES (?,?,?,?,?)").setParameter(1,c.org.valor()).setParameter(2,p).setParameter(3,tipo).setParameter(4,humano==null?"SISTEMA:motor:teste":"HUMANO:"+humano).setParameter(5,quando).executeUpdate());}
    private String json(Ctx c){return texto("SELECT retrato::text FROM resumo_diario WHERE org_id=? AND gestor_id=?",c.org.valor(),c.gestor);}
    private int array(String j,String nome){return arvore(j).path(nome).size();}
    private com.fasterxml.jackson.databind.JsonNode arvore(String j){try{return new com.fasterxml.jackson.databind.ObjectMapper().readTree(j);}catch(Exception e){throw new RuntimeException(e);}}
    private long num(String sql,Object... p){return ((Number)QuarkusTransaction.requiringNew().call(()->{var q=em.createNativeQuery(sql);for(int i=0;i<p.length;i++)q.setParameter(i+1,p[i]);return q.getSingleResult();})).longValue();}
    private String texto(String sql,Object... p){return String.valueOf(QuarkusTransaction.requiringNew().call(()->{var q=em.createNativeQuery(sql);for(int i=0;i<p.length;i++)q.setParameter(i+1,p[i]);return q.getSingleResult();}));}
    private Map<String,Object> headers(Ctx c){return Map.of("X-Org-Id",c.org.valor().toString(),"X-Pessoa-Id",c.gestor.toString());}
    private record Ctx(OrgId org,UUID gestor){}
}
