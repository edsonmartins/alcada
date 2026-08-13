package app.alcada.autonomia.internal;

import java.util.Optional;
import java.util.UUID;
import app.alcada.autonomia.port.DecisoesRetorno;
import app.alcada.plataforma.multitenancy.port.ContextoPessoa;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v1/retornos")
@Produces(MediaType.APPLICATION_JSON)
public class RetornoResource {
    private final DecisoesRetorno retornos;private final ContextoTenant tenant;private final ContextoPessoa pessoa;
    public RetornoResource(DecisoesRetorno retornos,ContextoTenant tenant,ContextoPessoa pessoa){this.retornos=retornos;this.tenant=tenant;this.pessoa=pessoa;}

    @POST @Path("/{id}/decisao")
    public Response decidir(@PathParam("id") String id,@HeaderParam("Idempotency-Key") String chave,DecisaoRequest req){
        Optional<OrgId> org=tenant.atual();Optional<UUID> gestor=pessoa.atual();
        if(org.isEmpty()||gestor.isEmpty())return problema(400,"requisicao.invalida","X-Org-Id e X-Pessoa-Id são obrigatórios");
        try{retornos.decidir(org.get(),gestor.get(),UUID.fromString(id),req==null?null:req.decisao(),chave);return Response.noContent().build();}
        catch(DecisoesRetorno.NaoEncontrado e){return problema(404,"retorno.nao_encontrado",e.getMessage());}
        catch(DecisoesRetorno.Proibido e){return problema(403,"retorno.proibido",e.getMessage());}
        catch(DecisoesRetorno.Conflito e){return problema(409,"retorno.conflito",e.getMessage());}
        catch(IllegalArgumentException e){return problema(422,"retorno.decisao_invalida",e.getMessage());}
    }
    private static Response problema(int status,String tipo,String detalhe){return Response.status(status).type("application/problem+json")
            .entity(new Problema("urn:alcada:"+tipo,detalhe,status)).build();}
    public record DecisaoRequest(String decisao){}
    public record Problema(String type,String detail,int status){}
}
