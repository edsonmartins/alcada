package app.alcada.captura.port;

/**
 * Mensagem de saída para o canal de origem (ADR-0021). A Alçada não fala com
 * WhatsApp nem e-mail diretamente — isto vai ao Linktor pelo outbox.
 */
public record EnviarMensagem(
        String canal,
        String destino,
        String texto,
        String responderA,       // thread/mensagem a responder, opcional
        String idempotencyKey) {
}
