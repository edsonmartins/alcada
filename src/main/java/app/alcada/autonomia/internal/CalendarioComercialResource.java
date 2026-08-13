package app.alcada.autonomia.internal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

@Path("/v1/calendario-comercial")
@Produces(MediaType.APPLICATION_JSON)
public class CalendarioComercialResource {
    private final CalendarioComercialJdbc calendario;
    private final ContextoTenant contexto;
    private final SecurityIdentity identidade;
    public CalendarioComercialResource(CalendarioComercialJdbc calendario,ContextoTenant contexto,SecurityIdentity identidade){
        this.calendario=calendario;this.contexto=contexto;this.identidade=identidade;}

    @GET public Response obter(){OrgId org=org();if(org==null)return problema(400,"requisicao.invalida","X-Org-Id é obrigatório");
        var c=calendario.configuracao(org);return Response.ok(dto(c)).build();}

    @PUT @Transactional public Response salvar(@HeaderParam("X-Papel") String papel,ConfiguracaoRequest req){
        OrgId org=org();if(org==null)return problema(400,"requisicao.invalida","X-Org-Id é obrigatório");
        boolean admin=!identidade.isAnonymous()?identidade.hasRole("ADMIN"):"ADMIN".equals(papel);
        if(!admin)return problema(403,"acesso.negado","papel ADMIN é obrigatório");
        if(req==null)return problema(400,"calendario.invalido","configuração obrigatória");
        try{Set<Integer> dias=new LinkedHashSet<>(req.diasUteis()==null?List.of():req.diasUteis());Map<LocalDate,String> fs=new LinkedHashMap<>();
            if(req.feriados()!=null)for(Feriado f:req.feriados())fs.put(LocalDate.parse(f.data()),f.nome());
            calendario.salvar(org,req.timezone(),dias,LocalTime.parse(req.inicio()),LocalTime.parse(req.fim()),fs);
            return Response.ok(dto(calendario.configuracao(org))).build();
        }catch(Exception e){return problema(422,"calendario.invalido",e.getMessage());}}

    private OrgId org(){return contexto.atual().orElse(null);}
    private static ConfiguracaoResponse dto(CalendarioComercialJdbc.Configuracao c){return new ConfiguracaoResponse(c.timezone(),c.diasUteis().stream().sorted().toList(),c.inicio().toString(),c.fim().toString(),c.feriados().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->new Feriado(e.getKey().toString(),e.getValue())).toList());}
    private static Response problema(int s,String t,String d){return Response.status(s).type("application/problem+json").entity(new Problema("urn:alcada:"+t,d,s)).build();}
    public record ConfiguracaoRequest(String timezone,List<Integer> diasUteis,String inicio,String fim,List<Feriado> feriados){}
    public record ConfiguracaoResponse(String timezone,List<Integer> diasUteis,String inicio,String fim,List<Feriado> feriados){}
    public record Feriado(String data,String nome){}
    public record Problema(String type,String detail,int status){}
}
