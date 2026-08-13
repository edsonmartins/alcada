package app.alcada.notificacao.internal;

import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import app.alcada.plataforma.multitenancy.port.ContextoPessoa;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

@Path("/v1/preferencias-notificacao")
@Produces(MediaType.APPLICATION_JSON)
public class PreferenciaNotificacaoResource {
    private final EntityManager em;private final ContextoTenant tenant;private final ContextoPessoa pessoa;
    private final ResumosExcecao resumos;
    public PreferenciaNotificacaoResource(EntityManager em,ContextoTenant tenant,ContextoPessoa pessoa,ResumosExcecao resumos){this.em=em;this.tenant=tenant;this.pessoa=pessoa;this.resumos=resumos;}
    @GET public Response obter(){var c=ctx();if(c==null)return problema(400,"requisicao.invalida");
        @SuppressWarnings("unchecked") var rs=em.createNativeQuery("SELECT canal,resumo_inicio,resumo_fim,ativa FROM preferencia_notificacao WHERE org_id=? AND gestor_id=?")
                .setParameter(1,c.org.valor()).setParameter(2,c.pessoa).getResultList();
        if(rs.isEmpty())return Response.ok(new Resposta("EMAIL",null,null,false)).build();Object[] r=(Object[])rs.getFirst();
        return Response.ok(new Resposta((String)r[0],texto(r[1]),texto(r[2]),(Boolean)r[3])).build();}
    @PUT @Transactional public Response salvar(Pedido p){var c=ctx();if(c==null)return problema(400,"requisicao.invalida");
        try{if(p==null||!"EMAIL".equals(p.canal()))throw new IllegalArgumentException("somente EMAIL está disponível para pessoa interna");
            LocalTime inicio=hora(p.resumoInicio()),fim=hora(p.resumoFim());if(inicio==null&&fim==null&&Boolean.TRUE.equals(p.ativa()))throw new IllegalArgumentException("informe ao menos um horário");
            em.createNativeQuery("""
                INSERT INTO preferencia_notificacao(org_id,gestor_id,canal,resumo_inicio,resumo_fim,ativa)
                VALUES (?,?,?,?,?,?) ON CONFLICT(org_id,gestor_id) DO UPDATE SET canal=EXCLUDED.canal,
                resumo_inicio=EXCLUDED.resumo_inicio,resumo_fim=EXCLUDED.resumo_fim,ativa=EXCLUDED.ativa,atualizada_em=now()
                """).setParameter(1,c.org.valor()).setParameter(2,c.pessoa).setParameter(3,p.canal())
                .setParameter(4,inicio).setParameter(5,fim).setParameter(6,Boolean.TRUE.equals(p.ativa())).executeUpdate();
            resumos.agendar(c.org,c.pessoa,inicio,fim,Boolean.TRUE.equals(p.ativa()));return Response.noContent().build();
        }catch(IllegalArgumentException e){return Response.status(422).type("application/problem+json").entity(new Problema("urn:alcada:preferencia.invalida",e.getMessage(),422)).build();}}
    private Ctx ctx(){Optional<OrgId> o=tenant.atual();Optional<UUID> p=pessoa.atual();return o.isEmpty()||p.isEmpty()?null:new Ctx(o.get(),p.get());}
    private static LocalTime hora(String s){return s==null||s.isBlank()?null:LocalTime.parse(s);}
    private static String texto(Object v){return v==null?null:(v instanceof LocalTime t?t:((java.sql.Time)v).toLocalTime()).toString();}
    private static Response problema(int s,String t){return Response.status(s).type("application/problem+json").entity(new Problema("urn:alcada:"+t,"X-Org-Id e X-Pessoa-Id são obrigatórios",s)).build();}
    private record Ctx(OrgId org,UUID pessoa){} public record Pedido(String canal,String resumoInicio,String resumoFim,Boolean ativa){} public record Resposta(String canal,String resumoInicio,String resumoFim,boolean ativa){} public record Problema(String type,String detail,int status){}
}
