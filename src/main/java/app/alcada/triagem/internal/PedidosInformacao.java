package app.alcada.triagem.internal;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import app.alcada.autonomia.port.CorrelacoesRetorno;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.port.MensagemOutbox;
import app.alcada.plataforma.outbox.port.Outbox;
import app.alcada.plataforma.scheduler.port.Agenda;
import app.alcada.plataforma.scheduler.port.TarefaAgendada;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Pedido confirmado pelo gestor; não é Pendência nem Delegação (ADR-0030). */
@ApplicationScoped
public class PedidosInformacao implements app.alcada.triagem.port.RetornoPedidoInformacao {
    public static final String JOB_VENCER = "VENCER_PEDIDO_INFORMACAO";
    private final EntityManager em;
    private final Outbox outbox;
    private final Agenda agenda;
    private final Trilha trilha;
    private final CorrelacoesRetorno correlacoes;

    @ConfigProperty(name="alcada.pedido-informacao.janela", defaultValue="PT5M")
    Duration janela;

    public PedidosInformacao(EntityManager em, Outbox outbox, Agenda agenda, Trilha trilha,
                             CorrelacoesRetorno correlacoes) {
        this.em=em; this.outbox=outbox; this.agenda=agenda; this.trilha=trilha; this.correlacoes=correlacoes;
    }

    @Transactional
    public UUID criar(OrgId org, UUID pendenciaId, UUID contatoId, String pergunta,
                      OffsetDateTime prazo, UUID gestorId) {
        if (pergunta == null || pergunta.trim().length() < 3 || pergunta.trim().length() > 1000)
            throw new Invalido("pergunta deve ter entre 3 e 1000 caracteres");
        if (prazo == null || !prazo.isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
            throw new Invalido("prazo deve estar no futuro");
        String status;
        try {
            status=(String)em.createNativeQuery("SELECT status FROM pendencia WHERE org_id=? AND id=? FOR UPDATE")
                    .setParameter(1,org.valor()).setParameter(2,pendenciaId).getSingleResult();
        } catch (NoResultException e) { throw new Inexistente(); }
        if (!"ENTRADA".equals(status)) throw new Conflito("Pendência não está na Entrada");
        Object[] contato;
        try {
            contato=(Object[])em.createNativeQuery("SELECT canal,endereco FROM contato_externo WHERE org_id=? AND id=?")
                    .setParameter(1,org.valor()).setParameter(2,contatoId).getSingleResult();
        } catch (NoResultException e) { throw new Inexistente(); }
        if (!"WHATSAPP".equals(contato[0])) throw new Invalido("pedido por e-mail aguarda integração Linktor");
        Number abertos=(Number)em.createNativeQuery("SELECT count(*) FROM pedido_informacao WHERE org_id=? AND pendencia_id=?"
                +" AND estado IN ('AGUARDANDO_ENVIO','AGUARDANDO_RESPOSTA')")
                .setParameter(1,org.valor()).setParameter(2,pendenciaId).getSingleResult();
        if (abertos.longValue()>0) throw new Conflito("já existe pedido de informação aberto");

        UUID id=UUID.randomUUID();
        int ocorrencia=((Number)em.createNativeQuery("SELECT ocorrencia FROM pendencia WHERE org_id=? AND id=?")
                .setParameter(1,org.valor()).setParameter(2,pendenciaId).getSingleResult()).intValue()+1;
        em.createNativeQuery("INSERT INTO pedido_informacao(id,org_id,pendencia_id,contato_id,pergunta,prazo,gestor_id) VALUES (?,?,?,?,?,?,?)")
                .setParameter(1,id).setParameter(2,org.valor()).setParameter(3,pendenciaId)
                .setParameter(4,contatoId).setParameter(5,pergunta.trim()).setParameter(6,prazo).setParameter(7,gestorId).executeUpdate();
        em.createNativeQuery("UPDATE pendencia SET status='DORMINDO',volta_em=?,ocorrencia=? WHERE org_id=? AND id=?")
                .setParameter(1,prazo).setParameter(2,ocorrencia).setParameter(3,org.valor()).setParameter(4,pendenciaId)
                .executeUpdate();
        correlacoes.criarParaPedido(org,id,(String)contato[0],(String)contato[1],prazo.plusDays(30));
        String payload="{\"pedido_id\":\""+id+"\",\"pendencia_id\":\""+pendenciaId
                +"\",\"canal\":\""+contato[0]+"\",\"endereco\":\""+esc((String)contato[1])
                +"\",\"pergunta\":\""+esc(pergunta.trim())+"\"}";
        outbox.publicarApos(new MensagemOutbox(org,"PEDIDO_INFORMACAO",payload,id+":PEDIDO_INFORMACAO"),
                OffsetDateTime.now(ZoneOffset.UTC).plus(janela));
        agenda.agendar(new TarefaAgendada(org,JOB_VENCER,id.toString(),prazo,
                "{\"pedido_id\":\""+id+"\"}"));
        trilha.registrar(new EventoTrilha(org,pendenciaId,TipoEvento.PEDIDO_INFORMACAO_CRIADO,
                Ator.humano(gestorId),"ENTRADA","DORMINDO",null,
                "{\"pedido_id\":\""+id+"\",\"prazo\":\""+prazo+"\"}"));
        return id;
    }

    @Transactional
    @Override public boolean responder(OrgId org, UUID pedidoId) {
        Object[] p=buscarParaUpdate(org,pedidoId);
        if (p==null || !"AGUARDANDO_RESPOSTA".equals(p[1]) && !"AGUARDANDO_ENVIO".equals(p[1])) return false;
        UUID pendencia=(UUID)p[0];
        em.createNativeQuery("UPDATE pedido_informacao SET estado='RESPONDIDO',respondida_em=now() WHERE org_id=? AND id=?")
                .setParameter(1,org.valor()).setParameter(2,pedidoId).executeUpdate();
        em.createNativeQuery("UPDATE pendencia SET status='ENTRADA',volta_em=NULL WHERE org_id=? AND id=? AND status='DORMINDO'")
                .setParameter(1,org.valor()).setParameter(2,pendencia).executeUpdate();
        trilha.registrar(new EventoTrilha(org,pendencia,TipoEvento.PEDIDO_INFORMACAO_RESPONDIDO,
                Ator.sistemaMotor("pedido-informacao"),"DORMINDO","ENTRADA",null,
                "{\"pedido_id\":\""+pedidoId+"\"}"));
        return true;
    }

    @Transactional
    public void vencer(OrgId org, UUID pedidoId) {
        Object[] p=buscarParaUpdate(org,pedidoId);
        if (p==null || !"AGUARDANDO_RESPOSTA".equals(p[1]) && !"AGUARDANDO_ENVIO".equals(p[1])) return;
        UUID pendencia=(UUID)p[0];
        em.createNativeQuery("UPDATE pedido_informacao SET estado='VENCIDO',vencida_em=now() WHERE org_id=? AND id=?")
                .setParameter(1,org.valor()).setParameter(2,pedidoId).executeUpdate();
        em.createNativeQuery("UPDATE pendencia SET status='ENTRADA',volta_em=NULL WHERE org_id=? AND id=? AND status='DORMINDO'")
                .setParameter(1,org.valor()).setParameter(2,pendencia).executeUpdate();
        trilha.registrar(new EventoTrilha(org,pendencia,TipoEvento.PEDIDO_INFORMACAO_VENCIDO,
                Ator.sistemaMotor("pedido-informacao"),"DORMINDO","ENTRADA",null,
                "{\"pedido_id\":\""+pedidoId+"\"}"));
    }

    private Object[] buscarParaUpdate(OrgId org,UUID id){
        try{return (Object[])em.createNativeQuery("SELECT pendencia_id,estado FROM pedido_informacao WHERE org_id=? AND id=? FOR UPDATE")
                .setParameter(1,org.valor()).setParameter(2,id).getSingleResult();}catch(NoResultException e){return null;}}
    private static String esc(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
    public static class Invalido extends RuntimeException { public Invalido(String m){super(m);} }
    public static class Conflito extends RuntimeException { public Conflito(String m){super(m);} }
    public static class Inexistente extends RuntimeException {}
}
