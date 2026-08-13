package app.alcada.consulta.internal;

import java.time.*;
import java.util.*;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoRegistrado;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

/** Read model histórico; consulta, nunca fila operacional (ADR-0032). */
@Path("/v1/itens") @Produces(MediaType.APPLICATION_JSON)
public class ItensResource {
    private static final Set<String> STATUS=Set.of("ENTRADA","DELEGADA","AGENDADA","DORMINDO","FECHADA");
    private static final Set<String> CLASSES=Set.of("DECISAO","BLOQUEIO","ESTEIRA");
    private static final Set<String> NIVEIS=Set.of("N1","N2","N3");
    private final EntityManager em;private final ContextoTenant contexto;private final ConsultaTrilha trilha;
    public ItensResource(EntityManager em,ContextoTenant contexto,ConsultaTrilha trilha){this.em=em;this.contexto=contexto;this.trilha=trilha;}

    @GET @Transactional public Response listar(@QueryParam("q") String busca,@QueryParam("status") String status,
       @QueryParam("classe") String classe,@QueryParam("nivel") String nivel,@QueryParam("pessoaId") String pessoa,
       @QueryParam("de") String de,@QueryParam("ate") String ate,@QueryParam("origem") String origem,
       @DefaultValue("0") @QueryParam("pagina") int pagina,@DefaultValue("25") @QueryParam("tamanho") int tamanho){
        OrgId org=contexto.atual().orElse(null);if(org==null)return erro(400,"org.ausente","X-Org-Id não resolvido");
        try{if(pagina<0||tamanho<1||tamanho>100)throw new IllegalArgumentException("pagina >= 0 e tamanho entre 1 e 100");
            validar(status,STATUS,"status");validar(classe,CLASSES,"classe");validar(nivel,NIVEIS,"nivel");
            UUID pessoaId=vazio(pessoa)?null:UUID.fromString(pessoa);OffsetDateTime desde=vazio(de)?null:OffsetDateTime.parse(de),antes=vazio(ate)?null:OffsetDateTime.parse(ate);
            Filtro f=new Filtro(org,busca,status,classe,nivel,pessoaId,desde,antes,origem);
            long total=contar(f);List<Item> itens=buscar(f,pagina,tamanho);return Response.ok(new Pagina(itens,pagina,tamanho,total)).build();
        }catch(IllegalArgumentException e){return erro(422,"consulta.filtro_invalido",e.getMessage());}}

    @GET @Path("/{id}") @Transactional public Response detalhe(@PathParam("id") String id){OrgId org=contexto.atual().orElse(null);if(org==null)return erro(400,"org.ausente","X-Org-Id não resolvido");
        UUID uuid;try{uuid=UUID.fromString(id);}catch(Exception e){return erro(404,"item.inexistente","item não encontrado");}
        List<Item> r=buscar(new Filtro(org,null,null,null,null,null,null,null,null),0,1,uuid);if(r.isEmpty())return erro(404,"item.inexistente","item não encontrado");
        List<Evento> eventos=trilha.daPendencia(org,uuid).stream().map(ItensResource::evento).toList();return Response.ok(new Detalhe(r.getFirst(),eventos)).build();}

    private long contar(Filtro f){QueryParts p=partes(f,false,null);var q=em.createNativeQuery("SELECT count(*) "+p.sql);bind(q,p.params);return ((Number)q.getSingleResult()).longValue();}
    private List<Item> buscar(Filtro f,int pagina,int tamanho){return buscar(f,pagina,tamanho,null);}
    private List<Item> buscar(Filtro f,int pagina,int tamanho,UUID id){QueryParts p=partes(f,true,id);String select="""
        SELECT p.id,p.titulo,p.classe,p.horizonte,p.status,p.quem_espera,p.o_que_trava,p.valor_em_jogo,
          p.origem_canal,COALESCE(g.nome,f.identificador),d.id,d.nivel,d.status,
          COALESCE(pe.nome,ce.nome),COALESCE((SELECT max(t.ocorrido_em) FROM trilha t WHERE t.org_id=p.org_id AND t.pendencia_id=p.id),p.criada_em),
          (SELECT count(*) FROM trilha t WHERE t.org_id=p.org_id AND t.pendencia_id=p.id)
        """+p.sql+" ORDER BY 15 DESC,p.id LIMIT ? OFFSET ?";var q=em.createNativeQuery(select);int n=bind(q,p.params);q.setParameter(++n,tamanho).setParameter(++n,pagina*tamanho);
        @SuppressWarnings("unchecked") List<Object[]> rs=q.getResultList();return rs.stream().map(ItensResource::item).toList();}
    private QueryParts partes(Filtro f,boolean joins,UUID id){List<Object> ps=new ArrayList<>();StringBuilder s=new StringBuilder(" FROM pendencia p ");
        s.append("LEFT JOIN LATERAL (SELECT x.* FROM delegacao x WHERE x.org_id=p.org_id AND x.pendencia_id=p.id ORDER BY x.criada_em DESC LIMIT 1) d ON true ");
        s.append("LEFT JOIN pessoa pe ON pe.org_id=p.org_id AND pe.id=d.dono_id LEFT JOIN contato_externo ce ON ce.org_id=p.org_id AND ce.id=d.contato_id ");
        s.append("LEFT JOIN fonte f ON f.org_id=p.org_id AND f.id=(SELECT eb.fonte_id FROM evento_bruto eb WHERE eb.org_id=p.org_id AND eb.thread_ref=p.origem_thread ORDER BY eb.recebido_em DESC LIMIT 1) ");
        s.append("LEFT JOIN grupo_acompanhado g ON g.org_id=p.org_id AND g.grupo_id=p.origem_thread AND g.fonte_id=f.id WHERE p.org_id=? ");ps.add(f.org.valor());
        if(id!=null){s.append("AND p.id=? ");ps.add(id);} if(!vazio(f.status)){s.append("AND p.status=? ");ps.add(f.status);}if(!vazio(f.classe)){s.append("AND p.classe=? ");ps.add(f.classe);}if(!vazio(f.nivel)){s.append("AND d.nivel=? ");ps.add(f.nivel);}if(f.pessoa!=null){s.append("AND d.dono_id=? ");ps.add(f.pessoa);}if(f.de!=null){s.append("AND p.criada_em>=? ");ps.add(f.de);}if(f.ate!=null){s.append("AND p.criada_em<=? ");ps.add(f.ate);}if(!vazio(f.origem)){s.append("AND COALESCE(g.nome,f.identificador,p.origem_canal,'') ILIKE ? ");ps.add("%"+f.origem+"%");}
        if(!vazio(f.busca)){s.append("AND (to_tsvector('portuguese',concat_ws(' ',p.titulo,p.quem_espera,p.o_que_trava,COALESCE(g.nome,f.identificador),pe.nome,ce.nome)) @@ websearch_to_tsquery('portuguese',?) OR EXISTS (SELECT 1 FROM documento_indice di WHERE di.org_id=p.org_id AND di.pendencia_id=p.id AND di.tsv @@ websearch_to_tsquery('portuguese',?))) ");ps.add(f.busca);ps.add(f.busca);}return new QueryParts(s.toString(),ps);}
    private static int bind(jakarta.persistence.Query q,List<Object> ps){int i=0;for(Object p:ps)q.setParameter(++i,p);return i;}
    private static Item item(Object[] r){UUID id=(UUID)r[0];String status=(String)r[4];List<Link> links=new ArrayList<>();links.add(new Link("TRILHA","/itens/"+id));if("ENTRADA".equals(status))links.add(new Link("ENTRADA","/"));if(r[10]!=null)links.add(new Link("DELEGACAO","/executor"));if(!"FECHADA".equals(status))links.add(new Link("BLOCO","/bloco/"+id));return new Item(id.toString(),(String)r[1],(String)r[2],(String)r[3],status,(String)r[5],(String)r[6],r[7]==null?null:((Number)r[7]).doubleValue(),(String)r[8],(String)r[9],r[10]==null?null:r[10].toString(),(String)r[11],(String)r[12],(String)r[13],odt(r[14]),((Number)r[15]).intValue(),links);}
    private static Evento evento(EventoRegistrado e){return new Evento(e.tipo(),e.ator(),e.ocorridoEm(),e.estadoAnterior(),e.estadoPosterior(),e.carga());}
    private static OffsetDateTime odt(Object v){if(v instanceof OffsetDateTime o)return o;if(v instanceof Instant i)return i.atOffset(ZoneOffset.UTC);return ((java.sql.Timestamp)v).toInstant().atOffset(ZoneOffset.UTC);}
    private static void validar(String v,Set<String>s,String nome){if(!vazio(v)&&!s.contains(v))throw new IllegalArgumentException(nome+" inválido");}private static boolean vazio(String s){return s==null||s.isBlank();}
    private static Response erro(int s,String t,String d){return Response.status(s).type("application/problem+json").entity(new Problema("urn:alcada:"+t,d,s)).build();}
    private record Filtro(OrgId org,String busca,String status,String classe,String nivel,UUID pessoa,OffsetDateTime de,OffsetDateTime ate,String origem){} private record QueryParts(String sql,List<Object> params){}
    public record Pagina(List<Item> itens,int pagina,int tamanho,long total){} public record Item(String id,String titulo,String classe,String horizonte,String status,String quemEspera,String oQueTrava,Double valorEmJogo,String origemCanal,String origem,String delegacaoId,String nivel,String estadoDelegacao,String executor,OffsetDateTime atividadeEm,int eventos,List<Link> links){} public record Link(String tipo,String href){} public record Detalhe(Item item,List<Evento> trilha){} public record Evento(String tipo,String ator,OffsetDateTime ocorridoEm,String estadoAnterior,String estadoPosterior,String carga){} public record Problema(String type,String detail,int status){}
}
