-- ============================================================================
-- V27 — Feedback de confirmação da voz (022): sinal de qualidade da interpretação
-- Quando o assistente pede "confirma?" (ADR-0014), o gestor CONFIRMA ou CORRIGE.
-- A taxa de correção mede quão frequentemente a interpretação erra. Agregado por
-- ORG (nunca por pessoa — ADR-0017 proíbe métrica de comportamento individual).
-- ============================================================================
CREATE TABLE voz_confirmacao (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id      uuid        NOT NULL,                               -- INV-15
    resultado   text        NOT NULL CHECK (resultado IN ('CONFIRMADO', 'CORRIGIDO')),
    ocorrido_em timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_voz_confirmacao_org ON voz_confirmacao (org_id, ocorrido_em);
