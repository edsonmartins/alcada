package app.alcada.plataforma.outbox.port;

/**
 * Porta de publicação do outbox. A escrita participa da MESMA transação da
 * transição de estado do chamador — se a transação reverte, nada é publicado.
 */
public interface Outbox {

    /**
     * Enfileira um efeito externo. Idempotente por {@code idempotencyKey}:
     * reenfileirar a mesma chave não cria duplicata.
     */
    void publicar(MensagemOutbox mensagem);
}
