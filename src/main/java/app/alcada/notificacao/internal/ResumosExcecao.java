package app.alcada.notificacao.internal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import app.alcada.autonomia.port.CalendarioComercial;
import app.alcada.autonomia.port.ExcecoesResumo;
import app.alcada.metricas.port.EstimativaDespacho;
import app.alcada.plataforma.multitenancy.port.FusoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.port.MensagemOutbox;
import app.alcada.plataforma.outbox.port.Outbox;
import app.alcada.plataforma.scheduler.port.Agenda;
import app.alcada.plataforma.scheduler.port.TarefaAgendada;
import app.alcada.triagem.port.ItensHojeResumo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Um envelope de despacho por gestor/período; vazio é silêncio (ADR-0034). */
@ApplicationScoped
public class ResumosExcecao {
    public static final String JOB="RESUMO_EXCECOES";
    private final EntityManager em;
    private final Agenda agenda;
    private final Outbox outbox;
    private final FusoTenant fuso;
    private final CalendarioComercial calendario;
    private final ItensHojeResumo hoje;
    private final ExcecoesResumo excecoes;
    private final EstimativaDespacho estimativa;
    private final ObjectMapper json;
    private final String webBaseUrl;

    public ResumosExcecao(EntityManager em,Agenda agenda,Outbox outbox,FusoTenant fuso,
            CalendarioComercial calendario,ItensHojeResumo hoje,ExcecoesResumo excecoes,
            EstimativaDespacho estimativa,ObjectMapper json,
            @ConfigProperty(name="alcada.web.base-url",defaultValue="http://localhost:5173") String webBaseUrl){
        this.em=em;this.agenda=agenda;this.outbox=outbox;this.fuso=fuso;this.calendario=calendario;
        this.hoje=hoje;this.excecoes=excecoes;this.estimativa=estimativa;this.json=json;
        this.webBaseUrl=webBaseUrl.replaceAll("/+$","");
    }

    public void agendar(OrgId org,UUID gestor,LocalTime inicio,LocalTime fim,boolean ativa){
        if(!ativa)return;if(inicio!=null)agendarUm(org,gestor,"INICIO",inicio);if(fim!=null)agendarUm(org,gestor,"FIM",fim);
    }

    private void agendarUm(OrgId org,UUID gestor,String periodo,LocalTime hora){
        OffsetDateTime quando=proximo(org,hora,OffsetDateTime.now(ZoneOffset.UTC));
        String payload="{\"gestor_id\":\""+gestor+"\",\"periodo\":\""+periodo+"\"}";
        int atualizou=em.createNativeQuery("""
                UPDATE job SET executar_em=?,payload=cast(? as jsonb),chave=?
                WHERE org_id=? AND tipo=? AND status='AGENDADO' AND executar_em>now()
                  AND payload->>'gestor_id'=? AND payload->>'periodo'=?
                """).setParameter(1,quando).setParameter(2,payload).setParameter(3,gestor+":"+periodo+":"+quando.toLocalDate())
                .setParameter(4,org.valor()).setParameter(5,JOB).setParameter(6,gestor.toString()).setParameter(7,periodo).executeUpdate();
        if(atualizou==0)agenda.agendar(new TarefaAgendada(org,JOB,gestor+":"+periodo+":"+quando.toLocalDate(),quando,payload));
    }

    @Transactional public void executar(OrgId org,UUID gestor,String periodo){
        @SuppressWarnings("unchecked") List<Object[]> prefs=em.createNativeQuery("""
                SELECT resumo_inicio,resumo_fim,ativa FROM preferencia_notificacao
                WHERE org_id=? AND gestor_id=?
                """).setParameter(1,org.valor()).setParameter(2,gestor).getResultList();
        if(prefs.isEmpty()||!Boolean.TRUE.equals(prefs.getFirst()[2]))return;
        Object[] pref=prefs.getFirst();
        LocalDate data=LocalDate.now(fuso.fuso(org));
        UUID resumoId=UUID.randomUUID();
        List<ItensHojeResumo.Item> itensHoje=hoje.listar(org);
        ExcecoesResumo.Conteudo ex=excecoes.listar(org,gestor,proximoPeriodo(org,pref));
        Set<UUID> distintas=new HashSet<>();
        itensHoje.forEach(i->distintas.add(i.pendenciaId()));
        ex.n2().forEach(i->distintas.add(i.pendenciaId()));
        ex.retornos().forEach(i->distintas.add(i.pendenciaId()));
        ex.escalonamentos().forEach(i->distintas.add(i.pendenciaId()));
        var estimados=estimativa.minutos(org,gestor,distintas.size());
        ObjectNode retrato=retrato(itensHoje,ex,estimados.isPresent()?estimados.getAsInt():null);
        int total=itensHoje.size()+(int)ex.totalN2()+(int)ex.totalRetornos()+(int)ex.totalEscalonamentos();
        int inseriu=em.createNativeQuery("""
                INSERT INTO resumo_diario(id,org_id,gestor_id,periodo,data_local,retrato,total_itens,estimativa_minutos)
                VALUES (?,?,?,?,?,cast(? as jsonb),?,?) ON CONFLICT (org_id,gestor_id,periodo,data_local) DO NOTHING
                """).setParameter(1,resumoId).setParameter(2,org.valor()).setParameter(3,gestor)
                .setParameter(4,periodo).setParameter(5,data).setParameter(6,retrato.toString())
                .setParameter(7,total).setParameter(8,estimados.isPresent()?estimados.getAsInt():null).executeUpdate();
        if(inseriu==1&&total>0){
            ObjectNode payload=json.createObjectNode().put("gestor_id",gestor.toString()).put("periodo",periodo)
                    .put("resumo_id",resumoId.toString()).put("nota",formatar(retrato));
            outbox.publicar(new MensagemOutbox(org,"RESUMO_EXCECOES",payload.toString(),
                    gestor+":resumo:"+periodo+":"+data));
        }
        LocalTime hora="INICIO".equals(periodo)?hora(pref[0]):hora(pref[1]);
        if(hora!=null)agendarUm(org,gestor,periodo,hora);
    }

    private ObjectNode retrato(List<ItensHojeResumo.Item> itensHoje,ExcecoesResumo.Conteudo ex,Integer minutos){
        ObjectNode r=json.createObjectNode();ArrayNode h=r.putArray("hoje");
        itensHoje.stream().limit(3).forEach(i->h.add(item(i.pendenciaId(),null,i.titulo(),i.justificativa(),null)));
        r.put("totalHoje",itensHoje.size());
        preencher(r,"n2PrestesExecutar","totalN2",ex.n2(),ex.totalN2(),"intervir");
        preencher(r,"retornosDecisao","totalRetornos",ex.retornos(),ex.totalRetornos(),"retorno");
        preencher(r,"escalonamentos","totalEscalonamentos",ex.escalonamentos(),ex.totalEscalonamentos(),"item");
        if(minutos==null)r.putNull("estimativaMinutos");else r.put("estimativaMinutos",minutos);
        return r;
    }

    private void preencher(ObjectNode r,String nome,String total,List<ExcecoesResumo.Item> itens,long quantidade,String tipo){
        ArrayNode a=r.putArray(nome);itens.stream().limit(3).forEach(i->{ObjectNode n=item(i.pendenciaId(),i.referenciaId(),i.titulo(),i.trecho(),i.quando());
            if("retorno".equals(tipo))n.put("acaoUrl",webBaseUrl+"/itens/"+i.pendenciaId()+"?retorno="+i.referenciaId());
            a.add(n);});r.put(total,quantidade);
    }
    private ObjectNode item(UUID pendencia,UUID referencia,String titulo,String detalhe,OffsetDateTime quando){
        ObjectNode n=json.createObjectNode().put("pendenciaId",pendencia.toString()).put("titulo",titulo)
                .put("acaoUrl",webBaseUrl+"/itens/"+pendencia);
        if(referencia!=null)n.put("referenciaId",referencia.toString());if(detalhe!=null)n.put("detalhe",detalhe);
        if(quando!=null)n.put("quando",quando.toString());return n;
    }
    private String formatar(ObjectNode r){
        StringBuilder s=new StringBuilder("Seu despacho por exceção:\n");
        secao(s,"Hoje",r,"hoje","totalHoje");secao(s,"N2 prestes a executar",r,"n2PrestesExecutar","totalN2");
        secao(s,"Retornos para decidir",r,"retornosDecisao","totalRetornos");
        secao(s,"Escalonamentos",r,"escalonamentos","totalEscalonamentos");
        if(!r.path("estimativaMinutos").isNull())s.append("Estimativa: ").append(r.path("estimativaMinutos").asInt()).append(" min.\n");
        s.append("Ação: despachar no Alçada.");return s.toString();
    }
    private static void secao(StringBuilder s,String titulo,ObjectNode r,String lista,String total){
        if(r.path(total).asLong()==0)return;s.append(titulo).append(" (").append(r.path(total).asLong()).append("):\n");
        r.path(lista).forEach(i->s.append("- ").append(i.path("titulo").asText()).append(" — ").append(i.path("acaoUrl").asText()).append('\n'));
    }
    private OffsetDateTime proximoPeriodo(OrgId org,Object[] pref){OffsetDateTime agora=OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1);OffsetDateTime melhor=null;
        for(Object v:new Object[]{pref[0],pref[1]}){LocalTime h=hora(v);if(h==null)continue;OffsetDateTime p=proximo(org,h,agora);if(melhor==null||p.isBefore(melhor))melhor=p;}
        return melhor==null?calendario.proximaAbertura(org,agora.plusDays(1)):melhor;}
    private OffsetDateTime proximo(OrgId org,LocalTime hora,OffsetDateTime agora){ZoneId z=fuso.fuso(org);ZonedDateTime local=agora.atZoneSameInstant(z);ZonedDateTime candidato=local.toLocalDate().atTime(hora).atZone(z);if(!candidato.toInstant().isAfter(agora.toInstant()))candidato=candidato.plusDays(1);return calendario.proximaAbertura(org,candidato.toOffsetDateTime());}
    private static LocalTime hora(Object v){return v==null?null:(v instanceof LocalTime t?t:((java.sql.Time)v).toLocalTime());}
}
