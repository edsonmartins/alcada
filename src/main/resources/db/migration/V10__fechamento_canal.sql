-- ============================================================================
-- V10 — Fechamento no canal de origem (ADR-0013, ADR-0021)
-- Origem denormalizada na pendência (o fechamento pode ocorrer após o expurgo
-- do bruto) e marca de falha registrada no outbox (varredor idempotente).
-- ============================================================================

-- Endereço de retorno do solicitante, guardado na captura (decisão: denormalizar)
ALTER TABLE pendencia ADD COLUMN origem_canal   text;   -- WHATSAPP | EMAIL | WEBHOOK
ALTER TABLE pendencia ADD COLUMN origem_destino text;   -- autor/endereço no canal
ALTER TABLE pendencia ADD COLUMN origem_thread  text;   -- thread para resposta encadeada

-- Varredor de mortos: marca as mensagens ERRO já registradas como FALHA_COMUNICACAO
ALTER TABLE outbox ADD COLUMN falha_registrada boolean NOT NULL DEFAULT false;

CREATE INDEX ix_outbox_mortos ON outbox (id) WHERE status = 'ERRO' AND NOT falha_registrada;
