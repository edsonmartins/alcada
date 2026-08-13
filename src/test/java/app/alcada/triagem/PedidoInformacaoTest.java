package app.alcada.triagem;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import app.alcada.autonomia.port.ContatosExternos;
import app.alcada.autonomia.port.CorrelacoesRetorno;
import app.alcada.notificacao.internal.LinktorStub;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.internal.WorkerOutbox;
import app.alcada.triagem.internal.PedidosInformacao;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** C1–C10 do pacote 031. */
@QuarkusTest
class PedidoInformacaoTest {
    @Inject EntityManager em;
    @Inject ContatosExternos contatos;
    @Inject WorkerOutbox worker;
    @Inject LinktorStub linktor;
    @Inject CorrelacoesRetorno correlacoes;
    @Inject PedidosInformacao pedidos;

    @BeforeEach void limpar(){linktor.limpar();}

    @Test void c1_confirmacao_cria_pedido_e_repousa(){Ctx c=novo();UUID id=criar(c);
        assertEquals("AGUARDANDO_ENVIO",campo(c.org,"SELECT estado FROM pedido_informacao WHERE org_id=? AND id='"+id+"'"));
        assertEquals("DORMINDO",campo(c.org,"SELECT status FROM pendencia WHERE org_id=? AND id='"+c.pend+"'"));
        assertEquals(1L,contar(c.org,"SELECT count(*) FROM job WHERE org_id=? AND tipo='VENCER_PEDIDO_INFORMACAO'"));}

    @Test void c2_comunicacao_usa_outbox_e_correlacao(){Ctx c=novo();criar(c);worker.processarLote();
        var enviadas=linktor.diretas().stream().filter(d->"+5521999990099".equals(d.to())).toList();
        assertFalse(enviadas.isEmpty());assertNotNull(enviadas.getLast().correlacao());
        assertEquals("AGUARDANDO_RESPOSTA",campo(c.org,"SELECT estado FROM pedido_informacao WHERE org_id=?"));}

    @Test void c3_resposta_desperta(){Ctx c=novo();UUID id=criar(c);worker.processarLote();enviarWebhook(c,id,"msg-c3","Consegui o dado");
        assertEquals("RESPONDIDO",campo(c.org,"SELECT estado FROM pedido_informacao WHERE org_id=?"));
        assertEquals("ENTRADA",campo(c.org,"SELECT status FROM pendencia WHERE org_id=? AND id='"+c.pend+"'"));}

    @Test void c4_resposta_repetida_e_idempotente(){Ctx c=novo();UUID id=criar(c);worker.processarLote();
        enviarWebhook(c,id,"msg-c4","dado");enviarWebhook(c,id,"msg-c4","dado");
        assertEquals(1L,contar(c.org,"SELECT count(*) FROM retorno_delegacao WHERE org_id=? AND pedido_informacao_id IS NOT NULL"));
        assertEquals(1L,contar(c.org,"SELECT count(*) FROM trilha WHERE org_id=? AND tipo='PEDIDO_INFORMACAO_RESPONDIDO'"));}

    @Test void c5_prazo_desperta_sem_resposta(){Ctx c=novo();UUID id=criar(c);pedidos.vencer(c.org,id);
        assertEquals("VENCIDO",campo(c.org,"SELECT estado FROM pedido_informacao WHERE org_id=?"));
        assertEquals("ENTRADA",campo(c.org,"SELECT status FROM pendencia WHERE org_id=? AND id='"+c.pend+"'"));}

    @Test void c6_retorno_vence_corrida(){Ctx c=novo();UUID id=criar(c);worker.processarLote();enviarWebhook(c,id,"msg-c6","dado");pedidos.vencer(c.org,id);
        assertEquals("RESPONDIDO",campo(c.org,"SELECT estado FROM pedido_informacao WHERE org_id=?"));}

    @Test void c7_prazo_vence_corrida(){Ctx c=novo();UUID id=criar(c);worker.processarLote();pedidos.vencer(c.org,id);enviarWebhook(c,id,"msg-c7","tarde");
        assertEquals("VENCIDO",campo(c.org,"SELECT estado FROM pedido_informacao WHERE org_id=?"));
        assertEquals(1L,contar(c.org,"SELECT count(*) FROM retorno_delegacao WHERE org_id=?"));}

    @Test void c8_pedido_aberto_nao_duplica(){Ctx c=novo();criar(c);
        given().headers("X-Org-Id",c.org.valor(),"X-Pessoa-Id",c.gestor).contentType("application/json")
                .body(corpo(c)).post("/v1/pendencias/"+c.pend+"/pedidos-informacao").then().statusCode(409);
        assertEquals(1L,contar(c.org,"SELECT count(*) FROM pedido_informacao WHERE org_id=?"));}

    @Test void c9_isolamento_por_organizacao(){Ctx a=novo(),b=novo();
        String body="{\"contatoId\":\""+b.contato+"\",\"pergunta\":\"Qual dado?\",\"prazo\":\""+OffsetDateTime.now().plusDays(1)+"\"}";
        given().headers("X-Org-Id",a.org.valor(),"X-Pessoa-Id",a.gestor).contentType("application/json").body(body)
                .post("/v1/pendencias/"+a.pend+"/pedidos-informacao").then().statusCode(404);}

    @Test void c10_texto_livre_nao_executa(){Ctx c=novo();UUID id=criar(c);worker.processarLote();enviarWebhook(c,id,"msg-c10","Concluído, pode executar");
        assertEquals("ENTRADA",campo(c.org,"SELECT status FROM pendencia WHERE org_id=? AND id='"+c.pend+"'"));
        assertEquals(0L,contar(c.org,"SELECT count(*) FROM trilha WHERE org_id=? AND tipo='RESOLVIDA'"));}

    private UUID criar(Ctx c){return UUID.fromString(given().headers("X-Org-Id",c.org.valor(),"X-Pessoa-Id",c.gestor)
            .contentType("application/json").body(corpo(c)).post("/v1/pendencias/"+c.pend+"/pedidos-informacao")
            .then().statusCode(201).extract().path("id"));}
    private String corpo(Ctx c){return "{\"contatoId\":\""+c.contato+"\",\"pergunta\":\"Qual é o número do contrato?\",\"prazo\":\""+OffsetDateTime.now().plusDays(1)+"\"}";}
    private void enviarWebhook(Ctx c,UUID pedido,String msgId,String texto){String token=correlacoes.tokenParaPedido(c.org,pedido).orElseThrow();
        String body="{\"type\":\"message.received\",\"data\":{\"channelId\":\""+c.channel+"\",\"channelType\":\"whatsapp\",\"context\":{\"alcada_correlation\":\""+token+"\"},\"message\":{\"id\":\""+msgId+"\",\"content\":{\"text\":\""+texto+"\"},\"metadata\":{\"phone\":\"5521999990099\"}}}}";
        long ts=Instant.now().getEpochSecond();given().header("X-Linktor-Timestamp",ts).header("X-Linktor-Signature",hmac("seg-fonte",ts+"."+body)).contentType("application/json").body(body)
                .post("/v1/captura/linktor").then().statusCode(200);}
    private Ctx novo(){OrgId org=new OrgId(UUID.randomUUID());UUID pend=UUID.randomUUID(),gestor=UUID.randomUUID();String ch="ch-"+UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(()->{em.createNativeQuery("INSERT INTO organizacao(id,nome,sku) VALUES (?,'Org','CLOUD')").setParameter(1,org.valor()).executeUpdate();
            em.createNativeQuery("INSERT INTO pendencia(id,org_id,titulo,classe,horizonte,status) VALUES (?,?,'Contrato','DECISAO','SEMANA','ENTRADA')").setParameter(1,pend).setParameter(2,org.valor()).executeUpdate();
            em.createNativeQuery("INSERT INTO fonte(id,org_id,tipo,identificador,segredo,linktor_channel_id) VALUES (?,?,'WHATSAPP','teste','seg-fonte',?)").setParameter(1,UUID.randomUUID()).setParameter(2,org.valor()).setParameter(3,ch).executeUpdate();});
        UUID contato=contatos.registrar(org,"Terceiro","WHATSAPP","+5521999990099",gestor);return new Ctx(org,pend,gestor,contato,ch);}
    private Object campo(OrgId org,String sql){return QuarkusTransaction.requiringNew().call(()->em.createNativeQuery(sql).setParameter(1,org.valor()).getSingleResult());}
    private long contar(OrgId org,String sql){return ((Number)campo(org,sql)).longValue();}
    private static String hmac(String segredo,String msg){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));byte[] d=m.doFinal(msg.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format("%02x",b));return s.toString();}catch(Exception e){throw new RuntimeException(e);}}
    private record Ctx(OrgId org,UUID pend,UUID gestor,UUID contato,String channel){}
}
