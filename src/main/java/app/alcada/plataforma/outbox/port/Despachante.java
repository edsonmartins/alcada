package app.alcada.plataforma.outbox.port;

/**
 * Entrega efetiva do efeito externo (notificação, canal via Linktor, webhook…).
 * Implementações concretas chegam nos pacotes de domínio; o worker do outbox
 * apenas invoca esta porta. A entrega é ao-menos-uma-vez, então o destino deve
 * ser idempotente pela chave da mensagem.
 */
public interface Despachante {

    void entregar(MensagemOutbox mensagem);
}
