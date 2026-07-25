package app.alcada.plataforma.outbox.port;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Efeito externo a ser publicado depois — nunca dentro do request (CLAUDE.md §4).
 *
 * @param org             tenant (INV-15)
 * @param tipo            nome do evento (docs/API.md §Eventos)
 * @param payloadJson     conteúdo do evento como JSON
 * @param idempotencyKey  chave única de idempotência
 *                        — (pendencia_id,transicao) ou Idempotency-Key do request
 */
public record MensagemOutbox(OrgId org, String tipo, String payloadJson, String idempotencyKey) {
}
