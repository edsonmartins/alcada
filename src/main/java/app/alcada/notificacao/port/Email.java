package app.alcada.notificacao.port;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Saída por e-mail (SMTP), simétrica ao {@link Canal} do WhatsApp. Contrato
 * explícito de sucesso e falha: a entrega pode falhar e o outbox reprocessa.
 */
public interface Email {

    /**
     * @return {@code true} se enviou agora; {@code false} se já enviado
     *         (idempotente por {@code idempotencyKey}).
     * @throws EmailIndisponivel quando a entrega falha (o outbox reprocessa).
     */
    boolean enviar(OrgId org, EnviarEmail mensagem);

    /** Falha de entrega — o efeito volta para retentativa no outbox. */
    class EmailIndisponivel extends RuntimeException {
        public EmailIndisponivel(String msg) {
            super(msg);
        }
    }
}
