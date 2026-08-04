-- ============================================================================
-- V37 — Conta de calendário do gestor (RFC-0009, fatia F2.3b)
-- OAuth é POR GESTOR, não por tenant: a agenda é pessoal. Os tokens ficam
-- cifrados (Cofre, AES-GCM) — vazar o banco sem a chave não vaza acesso à
-- agenda de ninguém. Escopo por organização (INV-15).
-- ============================================================================
CREATE TABLE conta_calendario (
    org_id        uuid        NOT NULL,
    pessoa_id     uuid        NOT NULL,
    provedor      text        NOT NULL CHECK (provedor IN ('GOOGLE', 'OUTLOOK')),
    access_token  text        NOT NULL,             -- cifrado
    refresh_token text,                             -- cifrado (ausente em alguns fluxos)
    expira_em     timestamptz,
    escopo        text,
    conectada_em  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, pessoa_id)
);
