package app.alcada.notificacao.internal;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import app.alcada.captura.port.AvisoGrupo;
import app.alcada.captura.port.EnviarAvisoGrupo;
import app.alcada.captura.port.EnviarDireto;
import app.alcada.captura.port.EnviarMensagem;
import app.alcada.autonomia.port.CorrelacoesRetorno;
import app.alcada.metricas.port.ProtecoesAgenda;
import app.alcada.notificacao.port.Calendario;
import app.alcada.notificacao.port.Canal;
import app.alcada.notificacao.port.CriarEvento;
import app.alcada.notificacao.port.Email;
import app.alcada.notificacao.port.EnviarEmail;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.port.Despachante;
import app.alcada.plataforma.outbox.port.MensagemOutbox;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Despachante de produção: consome o outbox e entrega ao canal de origem via
 * {@link Canal} (Linktor). É o que faltava para o worker do outbox sair do
 * no-op. Fronteira do ADR-0013: só o solicitante recebe estado/fechamento;
 * eventos internos ({@code delegacao.*}) NÃO geram saída ao solicitante.
 */
@ApplicationScoped
public class DespachanteCanal implements Despachante {

    private static final Logger LOG = Logger.getLogger(DespachanteCanal.class);

    /** Bloco padrão de um compromisso sem duração declarada (RFC-0009, questão 5). */
    private static final Duration DURACAO_PADRAO = Duration.ofHours(1);

    private final EntityManager em;
    private final Canal canal;
    private final Email email;
    private final Calendario calendario;
    private final Trilha trilha;
    private final AvisoGrupo avisoGrupo;
    private final ObjectMapper json = new ObjectMapper();
    private final CorrelacoesRetorno correlacoes;
    private final ProtecoesAgenda protecoesAgenda;
    private final FirebasePush push;

    @ConfigProperty(name = "alcada.web.base-url", defaultValue = "https://alcada.vendax.ai")
    String webBaseUrl;

    public DespachanteCanal(EntityManager em, Canal canal, Email email, Calendario calendario,
                            Trilha trilha, AvisoGrupo avisoGrupo, CorrelacoesRetorno correlacoes,
                            ProtecoesAgenda protecoesAgenda, FirebasePush push) {
        this.em = em;
        this.canal = canal;
        this.email = email;
        this.calendario = calendario;
        this.trilha = trilha;
        this.avisoGrupo = avisoGrupo;
        this.correlacoes = correlacoes;
        this.protecoesAgenda = protecoesAgenda;
        this.push = push;
    }

    @Override
    public void entregar(MensagemOutbox m) {
        switch (m.tipo()) {
            case "item.fechado" -> entregarFechamento(m);
            case "canal.resposta" -> entregarResposta(m);
            case "grupo.aviso" -> entregarAvisoGrupo(m);
            case "AVISO_REPASSE" -> entregarAvisoRepasse(m);
            case "AVISO_REPASSE_INTERNO" -> entregarAvisoRepasseInterno(m);
            case "PEDIDO_INFORMACAO" -> entregarPedidoInformacao(m);
            case "RESUMO_EXCECOES" -> entregarResumoExcecoes(m);
            case "EVENTO_CALENDARIO" -> entregarCompromisso(m);
            case "CANCELAR_EVENTO_CALENDARIO" -> cancelarCompromisso(m);
            case "PROTECAO_AGENDA" -> entregarProtecaoAgenda(m);
            default -> {
                // eventos internos (delegacao.executada/escalada/devolvida, …): sem saída ao solicitante
            }
        }
    }

    private void entregarFechamento(MensagemOutbox m) {
        UUID pendenciaId = UUID.fromString(campo(m.payloadJson(), "pendencia_id"));
        Object[] origem = origem(m.org(), pendenciaId);
        String canalNome = origem == null ? null : (String) origem[0];
        String destino = origem == null ? null : (String) origem[1];
        String conversationId = origem == null ? null : (String) origem[2]; // origem_thread

        // Linktor só responde a conversa que chegou por inbound (ADR-0025). Sem
        // conversationId não há canal para fechar — registra a IMPOSSIBILIDADE,
        // que é diferente de FALHA (não houve tentativa; não havia para onde).
        if (conversationId == null || conversationId.isBlank()) {
            trilha.registrar(new EventoTrilha(m.org(), pendenciaId, TipoEvento.COMUNICACAO_IMPOSSIVEL,
                    Ator.sistemaMotor("notificacao"), null, null, null,
                    "{\"motivo\":\"sem_conversa\"}"));
            return; // marcado como entregue pelo worker; não fica preso em retentativa
        }

        boolean novo = canal.enviar(m.org(), new EnviarMensagem(
                canalNome, destino,
                "Sua solicitação foi resolvida. (ref " + pendenciaId + ")",
                conversationId, m.idempotencyKey()));
        if (novo) {
            comunicada(m.org(), pendenciaId, canalNome);
        }
    }

    /**
     * Publica o aviso de bot visível no grupo (024 C6, ADR-0011 §2). Ao confirmar
     * o envio, marca aviso_em (via porta de captura) — só então o grupo é capturado.
     */
    private void entregarAvisoGrupo(MensagemOutbox m) {
        String p = m.payloadJson();
        String grupoId = campo(p, "grupo_id");
        // enviarAvisoGrupo retorna false quando já fora publicado (idempotente). Se
        // não lançou, a publicação está firme — marca aviso_em SEMPRE (o mark também
        // é idempotente). Gatear em `novo` deixaria o grupo sem captura para sempre
        // quando o envio deu certo mas a marca não commitou e o efeito é reentregue.
        canal.enviarAvisoGrupo(m.org(), new EnviarAvisoGrupo(
                campo(p, "channel_id"), grupoId, campo(p, "texto"), m.idempotencyKey()));
        avisoGrupo.marcarPublicado(m.org(), grupoId);
    }

    /**
     * Avisa um contato externo de repasse (RFC-0008 F1.3a). Inicia a mensagem no
     * canal do tenant (envio direto), sem conversa prévia, e emite COMUNICADA.
     * EMAIL fica para a fatia F1.3b (SMTP).
     */
    private void entregarAvisoRepasse(MensagemOutbox m) {
        String p = m.payloadJson();
        String canalTipo = campo(p, "canal");
        String endereco = campo(p, "endereco");
        UUID pendenciaId = UUID.fromString(campo(p, "pendencia_id"));
        UUID delegacaoId = UUID.fromString(campo(p, "delegacao_id"));
        String correlacao = correlacoes.tokenParaEnvio(m.org(),delegacaoId).orElse(null);
        String texto = mensagemRepasse(p, pendenciaId);

        boolean novo;
        if ("EMAIL".equals(canalTipo)) {
            novo = email.enviar(m.org(), new EnviarEmail(endereco, "Repasse no Alçada", texto,
                    m.idempotencyKey(), correlacao));
        } else if ("WHATSAPP".equals(canalTipo)) {
            String channelId = canalWhatsappDaOrg(m.org());
            if (channelId == null || channelId.isBlank()) {
                trilha.registrar(new EventoTrilha(m.org(), pendenciaId, TipoEvento.COMUNICACAO_IMPOSSIVEL,
                        Ator.sistemaMotor("notificacao"), null, null, null,
                        "{\"motivo\":\"sem_canal_whatsapp\"}"));
                return;
            }
            novo = canal.enviarDireto(m.org(),
                    new EnviarDireto(channelId, endereco, texto, m.idempotencyKey(), correlacao));
        } else {
            LOG.warnf("AVISO_REPASSE canal desconhecido: %s; marcado entregue", canalTipo);
            return;
        }
        if (novo) {
            comunicada(m.org(), pendenciaId, canalTipo);
        }
    }

    private String mensagemRepasse(String payload, UUID pendenciaId) {
        String titulo = campo(payload, "titulo");
        String mensagem = campo(payload, "mensagem");
        String nivel = campo(payload, "nivel");
        String autonomia = switch (nivel == null ? "N2" : nivel) {
            case "N1" -> "Você pode executar e depois informar o resultado.";
            case "N3" -> "Prepare a solução, mas aguarde a aprovação antes de executar.";
            default -> "Você pode prosseguir, a menos que receba uma orientação para parar.";
        };
        String contexto = mensagem == null || mensagem.isBlank()
                ? "Tarefa: " + (titulo == null || titulo.isBlank() ? "repasse recebido" : titulo) + "."
                : mensagem.trim();
        return "Olá! Você recebeu um repasse de uma pessoa que usa o Alçada.\n\n"
                + contexto + "\n\n" + autonomia
                + "\n\nResponda a esta mensagem citando-a para manter o acompanhamento."
                + " (ref " + pendenciaId + ")";
    }

    private void entregarPedidoInformacao(MensagemOutbox m) {
        String p=m.payloadJson();
        UUID pedidoId=UUID.fromString(campo(p,"pedido_id"));
        UUID pendenciaId=UUID.fromString(campo(p,"pendencia_id"));
        String channelId=canalWhatsappDaOrg(m.org());
        if(channelId==null||channelId.isBlank()){
            trilha.registrar(new EventoTrilha(m.org(),pendenciaId,TipoEvento.COMUNICACAO_IMPOSSIVEL,
                    Ator.sistemaMotor("notificacao"),null,null,null,"{\"motivo\":\"sem_canal_whatsapp\"}"));
            return;
        }
        String token=correlacoes.tokenParaPedido(m.org(),pedidoId).orElse(null);
        if(token==null) throw new Canal.CanalIndisponivel("correlação do pedido indisponível");
        boolean novo=canal.enviarDireto(m.org(),new EnviarDireto(channelId,campo(p,"endereco"),
                campo(p,"pergunta"),m.idempotencyKey(),token));
        if(novo){
            em.createNativeQuery("UPDATE pedido_informacao SET estado='AGUARDANDO_RESPOSTA'"
                    +" WHERE org_id=? AND id=? AND estado='AGUARDANDO_ENVIO'")
                    .setParameter(1,m.org().valor()).setParameter(2,pedidoId).executeUpdate();
            comunicada(m.org(),pendenciaId,"WHATSAPP");
        }
    }

    private void entregarAvisoRepasseInterno(MensagemOutbox m) {
        String p = m.payloadJson();
        String whatsapp = campo(p, "whatsapp");
        UUID pendenciaId = UUID.fromString(campo(p, "pendencia_id"));
        String delegacaoId = campo(p, "delegacao_id");
        UUID pessoaId = UUID.fromString(campo(p, "pessoa_id"));
        String titulo = campo(p, "titulo");
        String link = webBaseUrl.replaceAll("/+$", "") + "/app/delegacoes/" + delegacaoId;
        String texto = "Você recebeu um repasse no Alçada: " + titulo + ".\n\nAbra no aplicativo: " + link;
        push.enviar(m.org(), pessoaId, "Repasse recebido", titulo, delegacaoId);
        if (whatsapp == null || whatsapp.isBlank()) return;
        String channelId = canalWhatsappDaOrg(m.org());
        if (channelId == null || channelId.isBlank()) return;
        boolean novo = canal.enviarDireto(m.org(), new EnviarDireto(channelId, whatsapp, texto,
                m.idempotencyKey()));
        if (novo) comunicada(m.org(), pendenciaId, "WHATSAPP");
    }

    private void entregarResumoExcecoes(MensagemOutbox m) {
        UUID gestor=UUID.fromString(campo(m.payloadJson(),"gestor_id"));
        @SuppressWarnings("unchecked") java.util.List<Object[]> rs=em.createNativeQuery("""
                SELECT p.email,f.linktor_channel_id FROM pessoa p
                JOIN fonte f ON f.org_id=p.org_id AND f.tipo='EMAIL' AND f.ativa
                WHERE p.org_id=? AND p.id=? AND p.email IS NOT NULL AND f.linktor_channel_id IS NOT NULL
                ORDER BY f.id LIMIT 1
                """).setParameter(1,m.org().valor()).setParameter(2,gestor).getResultList();
        if(rs.isEmpty()) return; // gate explícito: sem identidade/canal, silêncio; job seguinte tentará de novo
        Object[] r=rs.getFirst();
        String nota=campo(m.payloadJson(),"nota");
        if(nota==null||nota.isBlank())return;
        canal.enviarDireto(m.org(),new EnviarDireto((String)r[1],(String)r[0],nota,m.idempotencyKey()));
    }

    /**
     * Põe o compromisso na agenda do gestor (RFC-0009 F2.3). Só chega aqui depois
     * da janela — antes disso a linha ficava no outbox e podia ser descartada
     * (INV-14). Sem calendário conectado, registra a impossibilidade e não fica
     * preso em retentativa; falha do provedor propaga e o outbox reprocessa.
     */
    private void entregarCompromisso(MensagemOutbox m) {
        String p = m.payloadJson();
        UUID lembreteId = UUID.fromString(campo(p, "lembrete_id"));
        UUID gestorId = UUID.fromString(campo(p, "gestor_id"));
        OffsetDateTime quando = OffsetDateTime.parse(campo(p, "quando"));
        String titulo = campo(p, "titulo");

        String eventoId;
        try {
            eventoId = calendario.criarEvento(m.org(), new CriarEvento(
                    gestorId, quando, DURACAO_PADRAO, titulo, m.idempotencyKey()));
        } catch (Calendario.SemConta e) {
            trilha.registrar(new EventoTrilha(m.org(), lembreteId, TipoEvento.FALHA_COMPROMISSO,
                    Ator.sistemaMotor("notificacao"), null, null, null,
                    "{\"motivo\":\"sem_calendario_conectado\"}"));
            return; // o lembrete continua valendo no Alçada; só não virou evento
        }
        if (eventoId == null) {
            return; // reprocesso: o evento já existe, nada a registrar de novo
        }
        em.createNativeQuery(
                "UPDATE pendencia SET evento_calendario_id = ? WHERE org_id = ? AND id = ?")
                .setParameter(1, eventoId).setParameter(2, m.org().valor())
                .setParameter(3, lembreteId).executeUpdate();
        trilha.registrar(new EventoTrilha(m.org(), lembreteId, TipoEvento.COMPROMISSO_AGENDADO,
                Ator.sistemaMotor("notificacao"), null, null, null,
                "{\"quando\":\"" + quando + "\"}"));
    }

    /**
     * Tira o compromisso da agenda quando o gestor cancela o lembrete depois do
     * evento existir. Sem conta conectada não há como remover — registra e segue,
     * em vez de reprocessar para sempre.
     */
    private void cancelarCompromisso(MensagemOutbox m) {
        String p = m.payloadJson();
        UUID lembreteId = UUID.fromString(campo(p, "lembrete_id"));
        UUID gestorId = UUID.fromString(campo(p, "gestor_id"));
        try {
            calendario.cancelarEvento(m.org(), gestorId, campo(p, "evento_id"));
        } catch (Calendario.SemConta e) {
            trilha.registrar(new EventoTrilha(m.org(), lembreteId, TipoEvento.FALHA_COMPROMISSO,
                    Ator.sistemaMotor("notificacao"), null, null, null,
                    "{\"motivo\":\"sem_calendario_conectado\",\"acao\":\"cancelar\"}"));
            return;
        }
        em.createNativeQuery(
                "UPDATE pendencia SET evento_calendario_id = NULL WHERE org_id = ? AND id = ?")
                .setParameter(1, m.org().valor()).setParameter(2, lembreteId).executeUpdate();
        trilha.registrar(new EventoTrilha(m.org(), lembreteId, TipoEvento.COMPENSACAO,
                Ator.sistemaMotor("notificacao"), null, null, null,
                "{\"o_que\":\"compromisso_cancelado\"}"));
    }

    private void entregarProtecaoAgenda(MensagemOutbox m) {
        String p=m.payloadJson();UUID protecao=UUID.fromString(campo(p,"protecao_id"));UUID pendencia=UUID.fromString(campo(p,"pendencia_id"));UUID gestor=UUID.fromString(campo(p,"gestor_id"));OffsetDateTime quando=OffsetDateTime.parse(campo(p,"quando"));long minutos=Long.parseLong(campo(p,"duracao_minutos"));
        try{String evento=calendario.criarEvento(m.org(),new CriarEvento(gestor,quando,Duration.ofMinutes(minutos),campo(p,"titulo"),m.idempotencyKey()));if(evento==null)return;protecoesAgenda.agendada(m.org(),protecao,evento);trilha.registrar(new EventoTrilha(m.org(),pendencia,TipoEvento.COMPROMISSO_AGENDADO,Ator.sistemaMotor("revisao"),null,null,null,"{\"protecao_id\":\""+protecao+"\",\"quando\":\""+quando+"\"}"));}catch(Calendario.SemConta e){protecoesAgenda.falhou(m.org(),protecao);trilha.registrar(new EventoTrilha(m.org(),pendencia,TipoEvento.FALHA_COMPROMISSO,Ator.sistemaMotor("revisao"),null,null,null,"{\"motivo\":\"sem_calendario_conectado\"}"));}
    }

    private String canalWhatsappDaOrg(OrgId org) {
        try {
            Object v = em.createNativeQuery(
                    "SELECT linktor_channel_id FROM fonte "
                    + "WHERE org_id = ? AND tipo = 'WHATSAPP' AND ativa = true LIMIT 1")
                    .setParameter(1, org.valor()).getSingleResult();
            return v == null ? null : v.toString();
        } catch (NoResultException e) {
            return null;
        }
    }

    private void entregarResposta(MensagemOutbox m) {
        String p = m.payloadJson();
        boolean novo = canal.enviar(m.org(), new EnviarMensagem(
                campo(p, "canal"), campo(p, "destino"), campo(p, "texto"),
                campo(p, "responder_a"), m.idempotencyKey()));
        String pend = campo(p, "pendencia_id");
        if (novo && pend != null) {
            comunicada(m.org(), UUID.fromString(pend), campo(p, "canal"));
        }
    }

    private void comunicada(OrgId org, UUID pendenciaId, String canalNome) {
        // destino vai como referência (canal), nunca identificador direto (ADR-0016)
        trilha.registrar(new EventoTrilha(org, pendenciaId, TipoEvento.COMUNICADA,
                Ator.sistemaMotor("notificacao"), null, null, null,
                "{\"canal\":\"" + canalNome + "\"}"));
    }

    private Object[] origem(OrgId org, UUID pendenciaId) {
        try {
            return (Object[]) em.createNativeQuery(
                    "SELECT origem_canal, origem_destino, origem_thread FROM pendencia WHERE org_id = ? AND id = ?")
                    .setParameter(1, org.valor()).setParameter(2, pendenciaId).getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    private String campo(String payload, String nome) {
        try {
            JsonNode n = json.readTree(payload).path(nome);
            return n.isMissingNode() || n.isNull() ? null : n.asText();
        } catch (Exception e) {
            return null;
        }
    }
}
