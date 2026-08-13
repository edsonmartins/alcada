package app.alcada.metricas.internal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.ContextoPessoa;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Superfície administrativa do piloto; fora da fila diária (028). */
@Path("/v1/piloto")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PilotoResource {
    private final PilotoJdbc piloto;
    private final ContextoTenant contexto;
    private final ContextoPessoa pessoa;
    private final SecurityIdentity identidade;

    public PilotoResource(PilotoJdbc piloto, ContextoTenant contexto, ContextoPessoa pessoa,
                          SecurityIdentity identidade) {
        this.piloto=piloto; this.contexto=contexto; this.pessoa=pessoa; this.identidade=identidade;
    }

    @GET @Path("/relatorio")
    public Response relatorio(@QueryParam("inicio") String inicio, @QueryParam("fim") String fim,
                              @HeaderParam("X-Alcada-Papel") String papel) {
        Base b=base(papel); if(b.erro()!=null) return b.erro();
        try {
            OffsetDateTime de=OffsetDateTime.parse(inicio), ate=OffsetDateTime.parse(fim);
            if(!de.isBefore(ate)) throw new IllegalArgumentException("início deve preceder fim");
            return Response.ok(piloto.relatorio(b.org(),de,ate)).build();
        } catch(Exception e){ return problema(400,"periodo.invalido",e.getMessage()); }
    }

    @POST @Path("/reconciliacoes")
    public Response reconciliar(Reconciliacao req,@HeaderParam("X-Alcada-Papel") String papel){
        Base b=base(papel); if(b.erro()!=null) return b.erro();
        try { UUID id=piloto.reconciliar(b.org(),LocalDate.parse(req.semana()),req.decisoesForaDaFila(),req.observacao(),b.pessoa());
            return Response.status(201).entity(new Criado(id.toString())).build();
        } catch(Exception e){ return problema(422,"reconciliacao.invalida",e.getMessage()); }
    }

    @GET @Path("/descartes/amostra")
    public Response amostra(@QueryParam("inicio") String inicio,@QueryParam("fim") String fim,
                            @QueryParam("limite") Integer limite,@QueryParam("semente") String semente,
                            @HeaderParam("X-Alcada-Papel") String papel){
        Base b=base(papel); if(b.erro()!=null) return b.erro();
        try { return Response.ok(piloto.amostra(b.org(),OffsetDateTime.parse(inicio),OffsetDateTime.parse(fim),
                limite==null?20:limite,semente)).build(); }
        catch(Exception e){ return problema(400,"periodo.invalido",e.getMessage()); }
    }

    @POST @Path("/descartes/{id}/avaliacoes")
    public Response avaliar(@PathParam("id") String id,Avaliacao req,@HeaderParam("X-Alcada-Papel") String papel){
        Base b=base(papel); if(b.erro()!=null) return b.erro();
        try { UUID criado=piloto.avaliar(b.org(),UUID.fromString(id),req.resultado(),b.pessoa());
            return Response.status(201).entity(new Criado(criado.toString())).build(); }
        catch(Exception e){ return problema(422,"avaliacao.invalida",e.getMessage()); }
    }

    private Base base(String papel){
        Optional<OrgId> org=contexto.atual(); Optional<UUID> p=pessoa.atual();
        if(org.isEmpty()||p.isEmpty()) return new Base(null,null,problema(400,"requisicao.invalida","org e pessoa são obrigatórios"));
        boolean admin=!identidade.isAnonymous()?identidade.hasRole("ADMIN"):"ADMIN".equals(papel);
        return admin?new Base(org.get(),p.get(),null):new Base(null,null,problema(403,"acesso.negado","papel ADMIN é obrigatório"));
    }
    private static Response problema(int s,String t,String d){return Response.status(s).type("application/problem+json").entity(new Problema("urn:alcada:"+t,d,s)).build();}
    private record Base(OrgId org,UUID pessoa,Response erro){}
    public record Reconciliacao(String semana,int decisoesForaDaFila,String observacao){}
    public record Avaliacao(String resultado){}
    public record Criado(String id){}
    public record Problema(String type,String detail,int status){}
}
