package app.alcada.notificacao.internal;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import app.alcada.captura.port.AvisoGrupo;
import app.alcada.captura.port.EnviarAvisoGrupo;
import app.alcada.captura.port.EnviarDireto;
import app.alcada.captura.port.EnviarMensagem;
import app.alcada.notificacao.port.Canal;
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

/**
 * Despachante de produção: consome o outbox e entrega ao canal de origem via
 * {@link Canal} (Linktor). É o que faltava para o worker do outbox sair do
 * no-op. Fronteira do ADR-0013: só o solicitante recebe estado/fechamento;
 * eventos internos ({@code delegacao.*}) NÃO geram saída ao solicitante.
 */
@ApplicationScoped
public class DespachanteCanal implements Despachante {

    private static final Logger LOG = Logger.getLogger(DespachanteCanal.class);

    private final EntityManager em;
    private final Canal canal;
    private final Trilha trilha;
    private final AvisoGrupo avisoGrupo;
    private final ObjectMapper json = new ObjectMapper();

    public DespachanteCanal(EntityManager em, Canal canal, Trilha trilha, AvisoGrupo avisoGrupo) {
        this.em = em;
        this.canal = canal;
        this.trilha = trilha;
        this.avisoGrupo = avisoGrupo;
    }

    @Override
    public void entregar(MensagemOutbox m) {
        switch (m.tipo()) {
            case "item.fechado" -> entregarFechamento(m);
            case "canal.resposta" -> entregarResposta(m);
            case "grupo.aviso" -> entregarAvisoGrupo(m);
            case "AVISO_REPASSE" -> entregarAvisoRepasse(m);
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

        if (!"WHATSAPP".equals(canalTipo)) {
            // EMAIL (SMTP) é a fatia F1.3b; sem canal ainda — não trava o outbox.
            LOG.infof("AVISO_REPASSE canal %s ainda sem entrega (F1.3b); marcado entregue", canalTipo);
            return;
        }
        String channelId = canalWhatsappDaOrg(m.org());
        if (channelId == null || channelId.isBlank()) {
            trilha.registrar(new EventoTrilha(m.org(), pendenciaId, TipoEvento.COMUNICACAO_IMPOSSIVEL,
                    Ator.sistemaMotor("notificacao"), null, null, null,
                    "{\"motivo\":\"sem_canal_whatsapp\"}"));
            return;
        }
        String texto = "Você recebeu um repasse no Alçada para acompanhar. (ref " + pendenciaId + ")";
        boolean novo = canal.enviarDireto(m.org(),
                new EnviarDireto(channelId, endereco, texto, m.idempotencyKey()));
        if (novo) {
            comunicada(m.org(), pendenciaId, canalTipo);
        }
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
