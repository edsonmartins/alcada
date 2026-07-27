-- ============================================================================
-- V25 — Pendência na linha represada de trajeto (023, C4)
-- Permite desfazer por item no resumo ao estacionar: descartar o efeito externo
-- represado de UMA pendência (o terceiro nunca é comunicado), sem tocar nas
-- demais do mesmo trajeto. Só é preenchido em linhas represadas (trajeto_id).
-- ============================================================================
ALTER TABLE outbox ADD COLUMN pendencia_id uuid;

CREATE INDEX ix_outbox_trajeto ON outbox (trajeto_id, pendencia_id)
    WHERE trajeto_id IS NOT NULL;
