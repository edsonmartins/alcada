-- ============================================================================
-- V6 — Descarte de captura (RFC-0001: taxa de descarte é métrica de saúde)
-- Registra o descarte por irrelevância com o motivo. Não guarda o texto — só a
-- fonte e o motivo, para a métrica. Descarte NÃO gera trilha (não há pendência).
-- ============================================================================
CREATE TABLE descarte_captura (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id      uuid        NOT NULL,                 -- INV-15
    fonte_id    uuid        NOT NULL,
    motivo      text        NOT NULL,                 -- SEM_RELEVANCIA | ...
    ocorrido_em timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_descarte_org ON descarte_captura (org_id, ocorrido_em);
