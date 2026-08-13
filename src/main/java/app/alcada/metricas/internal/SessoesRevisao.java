package app.alcada.metricas.internal;

import java.time.*;import java.util.*;
import app.alcada.metricas.port.*;import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.regras.port.*;import app.alcada.plataforma.trilha.port.*;import jakarta.enterprise.context.ApplicationScoped;import jakarta.persistence.*;import jakarta.transaction.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import app.alcada.plataforma.outbox.port.*;

@ApplicationScoped
public class SessoesRevisao implements ProtecoesAgenda {
 private final EntityManager em;private final RevisaoSemanal revisao;private final Mineracao mineracao;private final Regras regras;private final Trilha trilha;private final ObjectMapper json;private final Outbox outbox;
 public SessoesRevisao(EntityManager em,RevisaoSemanal revisao,Mineracao mineracao,Regras regras,Trilha trilha,ObjectMapper json,Outbox outbox){this.em=em;this.revisao=revisao;this.mineracao=mineracao;this.regras=regras;this.trilha=trilha;this.json=json;this.outbox=outbox;}

 @Transactional public SessaoRevisaoDados iniciar(OrgId org,UUID gestor){
   em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(?,0))").setParameter(1,org.valor()+":"+gestor).getSingleResult();
   @SuppressWarnings("unchecked") List<UUID> abertas=em.createNativeQuery("SELECT id FROM sessao_revisao WHERE org_id=? AND gestor_id=? AND status='ABERTA'")
     .setParameter(1,org.valor()).setParameter(2,gestor).getResultList();if(!abertas.isEmpty())return obter(org,gestor,abertas.getFirst());
   UUID id=UUID.randomUUID();em.createNativeQuery("INSERT INTO sessao_revisao(id,org_id,gestor_id) VALUES (?,?,?)").setParameter(1,id).setParameter(2,org.valor()).setParameter(3,gestor).executeUpdate();
     em.createNativeQuery("""
       INSERT INTO sessao_revisao_dependencia(org_id,sessao_id,pendencia_id)
       SELECT p.org_id,?,p.id FROM pendencia p WHERE p.org_id=? AND p.status<>'FECHADA' AND
       (p.status IN ('ENTRADA','AGENDADA') OR (p.status='DELEGADA' AND EXISTS
       (SELECT 1 FROM delegacao d WHERE d.org_id=p.org_id AND d.pendencia_id=p.id AND d.nivel='N3'
        AND d.status IN ('ABERTA','PROPOSTA','AGUARDANDO_JANELA'))))
       """).setParameter(1,id).setParameter(2,org.valor()).executeUpdate();return obter(org,gestor,id);
 }

 @Transactional public SessaoRevisaoDados obter(OrgId org,UUID gestor,UUID id){Object[] s=sessao(org,gestor,id);return montar(org,id,s,null);}
 @Transactional public SessaoRevisaoDados concluir(OrgId org,UUID gestor,UUID id){Object[] s=sessao(org,gestor,id);if("CONCLUIDA".equals(s[1]))return montar(org,id,s,lerResumo(s[4]));SessaoRevisaoDados.ResumoSessao resumo=resumo(org,id,odt(s[2]));
   if("ABERTA".equals(s[1]))em.createNativeQuery("UPDATE sessao_revisao SET status='CONCLUIDA',concluida_em=now(),resumo=cast(? as jsonb) WHERE org_id=? AND gestor_id=? AND id=? AND status='ABERTA'")
     .setParameter(1,escreverResumo(resumo)).setParameter(2,org.valor()).setParameter(3,gestor).setParameter(4,id).executeUpdate();
   Object[] atual=sessao(org,gestor,id);return montar(org,id,atual,resumo);}

 @Transactional public void observar(OrgId org,UUID gestor,String classe){if(!Set.of("DECISAO","BLOQUEIO","ESTEIRA").contains(classe))throw new IllegalArgumentException("classe inválida");
   List<PropostaRegra> propostas=mineracao.propostas(org);PropostaRegra proposta=propostas.stream().filter(p->p.classe().equals(classe)).findFirst().orElseThrow(()->new IllegalArgumentException("proposta inexistente"));
   em.createNativeQuery("INSERT INTO observacao_proposta_regra(org_id,classe,por) VALUES (?,?,?)").setParameter(1,org.valor()).setParameter(2,classe).setParameter(3,gestor).executeUpdate();
   if(!proposta.casos().isEmpty()){UUID pend=UUID.fromString(proposta.casos().getFirst().pendenciaId());trilha.registrar(new EventoTrilha(org,pend,TipoEvento.SUGESTAO_OBSERVADA,Ator.humano(gestor),null,null,null,"{\"classe\":\""+classe+"\",\"desfecho\":\"OBSERVAR\"}"));}}
 @Transactional public void deliberarRegra(OrgId org,UUID gestor,String classe,boolean aceitar){PropostaRegra p=mineracao.propostas(org).stream().filter(x->x.classe().equals(classe)).findFirst().orElseThrow(()->new IllegalArgumentException("proposta inexistente"));if(p.casos().isEmpty())throw new IllegalArgumentException("proposta sem evidência");
   if(aceitar){if(p.donoSugerido()==null)throw new IllegalArgumentException("proposta sem dono");regras.criar(org,classe,p.nivelSugerido(),UUID.fromString(p.donoSugerido()));}else regras.silenciar(org,classe,gestor);registrarSugestao(org,gestor,p,aceitar?TipoEvento.SUGESTAO_ACEITA:TipoEvento.SUGESTAO_RECUSADA,aceitar?"ACEITAR":"RECUSAR");}
 @Transactional public void promover(OrgId org,UUID gestor,String classe,String dono,String nivelAtual){SessaoRevisaoDados.CandidataNivel c=candidatas(org).stream().filter(x->x.classe().equals(classe)&&x.donoId().equals(dono)&&x.nivelAtual().equals(nivelAtual)).findFirst().orElseThrow(()->new IllegalArgumentException("candidata inelegível"));
   if(regras.existeRegraAtiva(org,classe))throw new IllegalArgumentException("classe já possui regra ativa");String max=regras.nivelMaximo(org,classe);if(max!=null&&rank(c.nivelSugerido())>rank(max))throw new IllegalArgumentException("nível excede limite");regras.criar(org,classe,c.nivelSugerido(),UUID.fromString(dono));UUID pend=UUID.fromString(c.fontes().getFirst().pendenciaId());trilha.registrar(new EventoTrilha(org,pend,TipoEvento.NIVEL_PROMOVIDO,Ator.humano(gestor),nivelAtual,c.nivelSugerido(),null,"{\"classe\":\""+classe+"\",\"ocorrencias\":"+c.ocorrencias()+"}"));}
 private void registrarSugestao(OrgId org,UUID gestor,PropostaRegra p,TipoEvento tipo,String desfecho){trilha.registrar(new EventoTrilha(org,UUID.fromString(p.casos().getFirst().pendenciaId()),tipo,Ator.humano(gestor),null,null,null,"{\"classe\":\""+p.classe()+"\",\"desfecho\":\""+desfecho+"\"}"));}
 private static int rank(String n){return Map.of("N3",1,"N2",2,"N1",3).get(n);}
 @Transactional public UUID protegerAgenda(OrgId org,UUID gestor,UUID sessao,OffsetDateTime inicio,int minutos){if(!inicio.isAfter(OffsetDateTime.now(ZoneOffset.UTC))||minutos<30||minutos>480)throw new IllegalArgumentException("horário ou duração inválidos");Object[] s=sessao(org,gestor,sessao);if(!"ABERTA".equals(s[1]))throw new IllegalArgumentException("sessão concluída");@SuppressWarnings("unchecked")List<UUID> refs=em.createNativeQuery("SELECT id FROM pendencia WHERE org_id=? AND horizonte='TRIMESTRE' AND status<>'FECHADA' ORDER BY criada_em LIMIT 1").setParameter(1,org.valor()).getResultList();if(refs.isEmpty())throw new IllegalArgumentException("sem invasão trimestral");UUID id=UUID.randomUUID();int n=em.createNativeQuery("INSERT INTO protecao_agenda_revisao(id,org_id,sessao_id,gestor_id,pendencia_ref,inicio,duracao_minutos) VALUES (?,?,?,?,?,?,?) ON CONFLICT(org_id,sessao_id) DO NOTHING").setParameter(1,id).setParameter(2,org.valor()).setParameter(3,sessao).setParameter(4,gestor).setParameter(5,refs.getFirst()).setParameter(6,inicio).setParameter(7,minutos).executeUpdate();if(n==0){return (UUID)em.createNativeQuery("SELECT id FROM protecao_agenda_revisao WHERE org_id=? AND sessao_id=?").setParameter(1,org.valor()).setParameter(2,sessao).getSingleResult();}String payload="{\"protecao_id\":\""+id+"\",\"pendencia_id\":\""+refs.getFirst()+"\",\"gestor_id\":\""+gestor+"\",\"quando\":\""+inicio+"\",\"duracao_minutos\":"+minutos+",\"titulo\":\"Espaço protegido — Horizonte TRIMESTRE\"}";outbox.publicarApos(new MensagemOutbox(org,"PROTECAO_AGENDA",payload,"revisao:protecao:"+id),OffsetDateTime.now(ZoneOffset.UTC).plusHours(4));return id;}
 @Override @Transactional public void agendada(OrgId org,UUID id,String evento){em.createNativeQuery("UPDATE protecao_agenda_revisao SET status='AGENDADA',evento_calendario_id=? WHERE org_id=? AND id=? AND status='PENDENTE'").setParameter(1,evento).setParameter(2,org.valor()).setParameter(3,id).executeUpdate();}
 @Override @Transactional public void falhou(OrgId org,UUID id){em.createNativeQuery("UPDATE protecao_agenda_revisao SET status='FALHA' WHERE org_id=? AND id=? AND status='PENDENTE'").setParameter(1,org.valor()).setParameter(2,id).executeUpdate();}

 private SessaoRevisaoDados montar(OrgId org,UUID id,Object[] s,SessaoRevisaoDados.ResumoSessao calculado){return new SessaoRevisaoDados(id.toString(),(String)s[1],odt(s[2]),odt(s[3]),revisao.calcular(org),mineracao.propostas(org),candidatas(org),trimestre(org),calculado!=null?calculado:("CONCLUIDA".equals(s[1])?lerResumo(s[4]):null));}
 private Object[] sessao(OrgId org,UUID gestor,UUID id){@SuppressWarnings("unchecked") List<Object[]> r=em.createNativeQuery("SELECT id,status,iniciada_em,concluida_em,resumo::text FROM sessao_revisao WHERE org_id=? AND gestor_id=? AND id=?").setParameter(1,org.valor()).setParameter(2,gestor).setParameter(3,id).getResultList();if(r.isEmpty())throw new NoResultException();return r.getFirst();}

 private List<SessaoRevisaoDados.CandidataNivel> candidatas(OrgId org){@SuppressWarnings("unchecked") List<Object[]> rs=em.createNativeQuery("""
   SELECT p.classe,d.dono_id,COALESCE(pe.nome,'Executor'),d.nivel,count(DISTINCT p.id)
   FROM delegacao d JOIN pendencia p ON p.org_id=d.org_id AND p.id=d.pendencia_id
   LEFT JOIN pessoa pe ON pe.org_id=d.org_id AND pe.id=d.dono_id
   WHERE d.org_id=? AND d.nivel IN ('N2','N3') AND d.status='EXECUTADA' AND d.criada_em>=now()-interval '90 days'
   AND NOT EXISTS(SELECT 1 FROM regra_autonomia ra WHERE ra.org_id=d.org_id AND ra.classe=p.classe AND ra.ativa)
   AND NOT EXISTS(SELECT 1 FROM trilha t WHERE t.org_id=d.org_id AND t.pendencia_id=p.id AND t.tipo IN ('DEVOLVIDA_PELO_EXECUTOR','ESCALADA'))
   GROUP BY p.classe,d.dono_id,pe.nome,d.nivel HAVING count(DISTINCT p.id)>=3 ORDER BY count(DISTINCT p.id) DESC
   """).setParameter(1,org.valor()).getResultList();List<SessaoRevisaoDados.CandidataNivel> out=new ArrayList<>();for(Object[] r:rs){String c=(String)r[0],dono=r[1].toString(),nivel=(String)r[3];out.add(new SessaoRevisaoDados.CandidataNivel(c,dono,(String)r[2],nivel,"N3".equals(nivel)?"N2":"N1",((Number)r[4]).longValue(),fontesNivel(org,c,UUID.fromString(dono),nivel)));}return out;}
 private List<SessaoRevisaoDados.Fonte> fontesNivel(OrgId org,String classe,UUID dono,String nivel){@SuppressWarnings("unchecked") List<Object[]> rs=em.createNativeQuery("SELECT DISTINCT p.id,p.titulo FROM delegacao d JOIN pendencia p ON p.org_id=d.org_id AND p.id=d.pendencia_id WHERE d.org_id=? AND p.classe=? AND d.dono_id=? AND d.nivel=? AND d.status='EXECUTADA' ORDER BY p.titulo LIMIT 5").setParameter(1,org.valor()).setParameter(2,classe).setParameter(3,dono).setParameter(4,nivel).getResultList();return rs.stream().map(r->fonte(r[0],r[1])).toList();}
 private SessaoRevisaoDados.ImpactoTrimestre trimestre(OrgId org){@SuppressWarnings("unchecked") List<Object[]> rs=em.createNativeQuery("SELECT id,titulo,valor_em_jogo FROM pendencia WHERE org_id=? AND horizonte='TRIMESTRE' AND status<>'FECHADA' ORDER BY valor_em_jogo DESC NULLS LAST LIMIT 20").setParameter(1,org.valor()).getResultList();double total=rs.stream().filter(r->r[2]!=null).mapToDouble(r->((Number)r[2]).doubleValue()).sum();return new SessaoRevisaoDados.ImpactoTrimestre(rs.size(),total,rs.stream().limit(5).map(r->fonte(r[0],r[1])).toList(),"/canais");}
 private SessaoRevisaoDados.ResumoSessao resumo(OrgId org,UUID id,OffsetDateTime inicio){Map<String,Long> c=new HashMap<>();@SuppressWarnings("unchecked") List<Object[]> rs=em.createNativeQuery("SELECT tipo,count(*) FROM trilha WHERE org_id=? AND ocorrido_em>=? GROUP BY tipo").setParameter(1,org.valor()).setParameter(2,inicio).getResultList();for(Object[]r:rs)c.put((String)r[0],((Number)r[1]).longValue());long inic=num("SELECT count(*) FROM sessao_revisao_dependencia WHERE org_id=? AND sessao_id=?",org.valor(),id),rest=num("""
   SELECT count(*) FROM sessao_revisao_dependencia x JOIN pendencia p ON p.org_id=x.org_id AND p.id=x.pendencia_id
   WHERE x.org_id=? AND x.sessao_id=? AND p.status<>'FECHADA' AND (p.status IN ('ENTRADA','AGENDADA') OR
   (p.status='DELEGADA' AND EXISTS(SELECT 1 FROM delegacao d WHERE d.org_id=p.org_id AND d.pendencia_id=p.id AND d.nivel='N3' AND d.status IN ('ABERTA','PROPOSTA','AGUARDANDO_JANELA'))))
   """,org.valor(),id);List<SessaoRevisaoDados.Fonte> rem=remanescentes(org,id);long trans=c.values().stream().mapToLong(Long::longValue).sum();return new SessaoRevisaoDados.ResumoSessao(c.getOrDefault("TRIADA",0L),c.getOrDefault("RESOLVIDA",0L),c.getOrDefault("REPASSADA",0L),c.getOrDefault("REPOUSADA",0L),c.getOrDefault("BLOCO_AGENDADO",0L),c.getOrDefault("SUGESTAO_ACEITA",0L),c.getOrDefault("SUGESTAO_RECUSADA",0L),c.getOrDefault("SUGESTAO_OBSERVADA",0L),c.getOrDefault("NIVEL_PROMOVIDO",0L),c.getOrDefault("COMPROMISSO_AGENDADO",0L),Math.max(0,inic-rest),rest,rem,trans==0);}
 private List<SessaoRevisaoDados.Fonte> remanescentes(OrgId org,UUID id){@SuppressWarnings("unchecked")List<Object[]>r=em.createNativeQuery("SELECT p.id,p.titulo FROM sessao_revisao_dependencia x JOIN pendencia p ON p.org_id=x.org_id AND p.id=x.pendencia_id WHERE x.org_id=? AND x.sessao_id=? AND p.status<>'FECHADA' ORDER BY p.criada_em LIMIT 10").setParameter(1,org.valor()).setParameter(2,id).getResultList();return r.stream().map(x->fonte(x[0],x[1])).toList();}
 private long num(String sql,Object...p){Query q=em.createNativeQuery(sql);for(int i=0;i<p.length;i++)q.setParameter(i+1,p[i]);return ((Number)q.getSingleResult()).longValue();}
 private static SessaoRevisaoDados.Fonte fonte(Object id,Object titulo){return new SessaoRevisaoDados.Fonte(id.toString(),(String)titulo,"/itens/"+id);}private static OffsetDateTime odt(Object v){if(v==null)return null;if(v instanceof OffsetDateTime o)return o;if(v instanceof Instant i)return i.atOffset(ZoneOffset.UTC);return ((java.sql.Timestamp)v).toInstant().atOffset(ZoneOffset.UTC);}
 private String escreverResumo(SessaoRevisaoDados.ResumoSessao r){try{return json.writeValueAsString(r);}catch(Exception e){throw new IllegalStateException("falha ao guardar resumo",e);}}
 private SessaoRevisaoDados.ResumoSessao lerResumo(Object v){try{return json.readValue((String)v,SessaoRevisaoDados.ResumoSessao.class);}catch(Exception e){throw new IllegalStateException("resumo inválido",e);}}
}
