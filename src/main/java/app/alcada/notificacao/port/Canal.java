package app.alcada.notificacao.port;

import app.alcada.captura.port.EnviarAvisoGrupo;
import app.alcada.captura.port.EnviarDireto;
import app.alcada.captura.port.EnviarMensagem;
import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Saída para o canal de origem via Linktor (ADR-0021). Contrato explícito de
 * sucesso E falha: a entrega pode falhar, e o outbox reprocessa.
 */
public interface Canal {

    /**
     * @return {@code true} se entregou agora; {@code false} se já entregue
     *         (idempotente por {@code idempotency_key}).
     * @throws CanalIndisponivel quando a entrega falha (o outbox reprocessa).
     */
    boolean enviar(OrgId org, EnviarMensagem mensagem);

    /**
     * Publica um aviso num GRUPO (024 C6, bot visível). Endereça o grupo pelo
     * chat_jid via o canal do Linktor, não por conversa 1:1.
     *
     * @return {@code true} se publicou agora; {@code false} se já publicado
     *         (idempotente por {@code idempotencyKey}).
     * @throws CanalIndisponivel quando a entrega falha (o outbox reprocessa).
     */
    boolean enviarAvisoGrupo(OrgId org, EnviarAvisoGrupo aviso);

    /**
     * Inicia uma mensagem a um destinatário (telefone/e-mail) num canal, SEM
     * conversa prévia — para avisar um contato externo de repasse (RFC-0008).
     *
     * @return {@code true} se enviou agora; {@code false} se já enviado
     *         (idempotente por {@code idempotencyKey}).
     * @throws CanalIndisponivel quando a entrega falha (o outbox reprocessa).
     */
    boolean enviarDireto(OrgId org, EnviarDireto mensagem);

    /** Falha de entrega — o efeito volta para retentativa no outbox. */
    class CanalIndisponivel extends RuntimeException {
        public CanalIndisponivel(String msg) {
            super(msg);
        }
    }
}
