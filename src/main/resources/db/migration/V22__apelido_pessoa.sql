-- ============================================================================
-- V22 — Apelidos de pessoas (022, memória durável do canal de voz)
-- Aprende como o gestor se refere a cada pessoa: ao confirmar um repasse por voz,
-- o termo falado ("Xandão") passa a resolver direto para a pessoa. Escopo por
-- (org, gestor) — cada gestor tem seu vocabulário (INV-15). Não é cadastro
-- manual (INV-02): é aprendido do uso.
-- ============================================================================
CREATE TABLE apelido_pessoa (
    org_id        uuid        NOT NULL,                    -- INV-15
    gestor_id     uuid        NOT NULL,                    -- de quem é o apelido
    termo         text        NOT NULL,                    -- normalizado (minúsculo, sem acento)
    pessoa_id     uuid        NOT NULL,                    -- para quem aponta
    atualizado_em timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, gestor_id, termo)
);
CREATE INDEX ix_apelido_pessoa_pessoa ON apelido_pessoa (org_id, pessoa_id);
