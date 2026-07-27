-- ============================================================================
-- V24 — Represamento de trajeto no outbox (023, ADR-0014 §4, INV-14)
-- Efeitos externos ditados EM_TRAJETO ficam represados: a linha nasce com
-- trajeto_id preenchido e o worker NÃO a emite enquanto isso. Ao estacionar +
-- confirmar o resumo, libera-se o lote (trajeto_id -> NULL) e o worker envia.
-- ============================================================================
ALTER TABLE outbox ADD COLUMN trajeto_id uuid;   -- NULL = livre; preenchido = represado

-- O worker só varre linhas livres: recria o índice parcial incluindo o filtro.
DROP INDEX IF EXISTS ix_outbox_claim;
CREATE INDEX ix_outbox_claim
    ON outbox (disponivel_em)
    WHERE status = 'PENDENTE' AND trajeto_id IS NULL;
