-- ============================================================================
-- V21 — Sincronização de comandos do canal móvel (021, RFC-0005/INV-13)
-- Idempotência por (org_id, comando_id): reenvio devolve o resultado gravado, não
-- re-executa. PostgreSQL, sem Redis (ADR-0023 sobrepõe a menção do ADR-0015).
-- ============================================================================
CREATE TABLE comando_movel (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       uuid        NOT NULL,                       -- INV-15
    comando_id   uuid        NOT NULL,                       -- gerado no device
    intencao     text        NOT NULL,
    status       text        NOT NULL,                       -- OK | IGNORADO | RECUSADO | ERRO
    detalhe      text,
    pendencia_id uuid,
    recebido_em  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (org_id, comando_id)
);
CREATE INDEX ix_comando_movel_org ON comando_movel (org_id, recebido_em);
