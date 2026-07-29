package app.alcada.captura.port;

/**
 * Aviso a publicar num GRUPO (024 C6, ADR-0011 §2 — bot visível). Diferente de
 * {@link EnviarMensagem}, o destino é o próprio grupo (chat_jid) via o canal do
 * Linktor, não uma conversa 1:1. A Alçada não fala com o WhatsApp — isto vai ao
 * Linktor pelo outbox.
 *
 * @param channelId       canal do Linktor (linktor_channel_id da fonte)
 * @param grupoId         chat_jid do grupo destino
 * @param texto           o aviso de transparência
 * @param idempotencyKey  chave única (ao-menos-uma-vez no outbox)
 */
public record EnviarAvisoGrupo(String channelId, String grupoId, String texto, String idempotencyKey) {
}
