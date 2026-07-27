-- ============================================================================
-- V23 — Preferências do gestor (022, memória durável do canal de voz)
-- Chave/valor por (org, gestor): aprende hábitos do gestor para preencher padrões
-- sem perguntar. Primeiro uso: nível de repasse ("nivel_repasse"). Escopo por org
-- (INV-15). Não é cadastro manual (INV-02) — é aprendido do uso.
-- ============================================================================
CREATE TABLE preferencia_gestor (
    org_id        uuid        NOT NULL,                    -- INV-15
    gestor_id     uuid        NOT NULL,
    chave         text        NOT NULL,
    valor         text        NOT NULL,
    atualizado_em timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, gestor_id, chave)
);
