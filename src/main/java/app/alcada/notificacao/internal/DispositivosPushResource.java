package app.alcada.notificacao.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import app.alcada.plataforma.cripto.port.Cofre;
import app.alcada.plataforma.multitenancy.port.*;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

@Path("/v1/dispositivos-push") @Produces(MediaType.APPLICATION_JSON)
public class DispositivosPushResource {
 private final EntityManager em;private final ContextoTenant org;private final ContextoPessoa pessoa;private final Cofre cofre;
 public DispositivosPushResource(EntityManager em,ContextoTenant org,ContextoPessoa pessoa,Cofre cofre){this.em=em;this.org=org;this.pessoa=pessoa;this.cofre=cofre;}
 @PUT @Transactional public Response salvar(Entrada e){var o=org.atual();var p=pessoa.atual();if(o.isEmpty()||p.isEmpty())return Response.status(400).build();
  if(e==null||e.instalacaoId()==null||e.token()==null||!("ANDROID".equals(e.plataforma())||"IOS".equals(e.plataforma())))return Response.status(422).build();
  UUID i;try{i=UUID.fromString(e.instalacaoId());}catch(Exception x){return Response.status(422).build();}
  em.createNativeQuery("""
   INSERT INTO dispositivo_push(org_id,pessoa_id,instalacao_id,plataforma,token_cifrado,token_hash)
   VALUES(?,?,?,?,?,?) ON CONFLICT(org_id,pessoa_id,instalacao_id) DO UPDATE SET plataforma=excluded.plataforma,
   token_cifrado=excluded.token_cifrado,token_hash=excluded.token_hash,atualizado_em=now()
   """)
   .setParameter(1,o.get().valor()).setParameter(2,p.get()).setParameter(3,i).setParameter(4,e.plataforma())
   .setParameter(5,cofre.cifrar(e.token())).setParameter(6,hex(sha(e.token()))).executeUpdate();return Response.noContent().build();}
 @DELETE @Path("/{id}") @Transactional public Response remover(@PathParam("id") String id){var o=org.atual();var p=pessoa.atual();if(o.isEmpty()||p.isEmpty())return Response.status(400).build();
  try{em.createNativeQuery("DELETE FROM dispositivo_push WHERE org_id=? AND pessoa_id=? AND instalacao_id=?")
   .setParameter(1,o.get().valor()).setParameter(2,p.get()).setParameter(3,UUID.fromString(id)).executeUpdate();return Response.noContent().build();}catch(Exception e){return Response.status(404).build();}}
 private static byte[] sha(String v){try{return MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
 private static String hex(byte[] b){return java.util.HexFormat.of().formatHex(b);} public record Entrada(String instalacaoId,String plataforma,String token){}
}
