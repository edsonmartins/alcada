package app.alcada.notificacao.port;

/** E-mail de saída (RFC-0008 F1.3b): aviso de repasse a um contato externo por e-mail. */
public record EnviarEmail(
        String to,
        String assunto,
        String texto,
        String idempotencyKey) {
}
