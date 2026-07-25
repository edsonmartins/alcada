-- ============================================================================
-- V13 — Silenciamento de proposta de regra (pacote 010, RFC-0003 §A)
-- Quando o gestor silencia uma classe, a mineração para de propô-la. Decisão do
-- gestor, escopada por organização (INV-15). Único por (org_id, classe).
-- ============================================================================
CREATE TABLE regra_silenciada (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        uuid        NOT NULL,
    classe        text        NOT NULL CHECK (classe IN ('DECISAO', 'BLOQUEIO', 'ESTEIRA')),
    por           uuid,                                   -- pessoa que silenciou (opcional)
    silenciada_em timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT regra_silenciada_unica UNIQUE (org_id, classe)
);

CREATE INDEX ix_regra_silenciada_org ON regra_silenciada (org_id, classe);
