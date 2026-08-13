package app.alcada.captura.port;

/**
 * Mensagem de saída INICIADA (sem conversa prévia) a um destinatário — usada
 * para avisar um contato externo de repasse (RFC-0008). Vai ao Linktor pelo
 * outbox, para o endpoint de envio direto (channel_id + to). Diferente de
 * {@link EnviarMensagem}, que só responde a uma conversa inbound (ADR-0025).
 */
public record EnviarDireto(
        String channelId,        // canal do tenant no Linktor
        String to,               // destinatário: telefone (E.164) ou e-mail
        String texto,
        String idempotencyKey,
        String correlacao) {
    public EnviarDireto(String channelId,String to,String texto,String idempotencyKey) {
        this(channelId,to,texto,idempotencyKey,null);
    }
}
