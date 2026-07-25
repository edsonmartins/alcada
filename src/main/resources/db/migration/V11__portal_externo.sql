-- ============================================================================
-- V11 — Portal externo (ADR-0013)
-- Token de portal (hash, escopo por pendência, expiração, revogação) e marca de
-- fechamento na pendência (para expirar o token junto com o fechamento + folga).
-- ============================================================================

-- Instante de fechamento — o token expira com ele mais uma folga curta
ALTER TABLE pendencia ADD COLUMN fechada_em timestamptz;

CREATE TABLE token_portal (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       uuid        NOT NULL,                 -- INV-15
    pendencia_id uuid        NOT NULL REFERENCES pendencia(id),
    token_hash   text        NOT NULL,                 -- SHA-256 do token; nunca o token cru
    o_que_falta  text,                                 -- texto CURADO (não derivado do estado interno)
    expira_em    timestamptz NOT NULL,
    revogado     boolean     NOT NULL DEFAULT false,
    criado_em    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT token_portal_hash_unico UNIQUE (token_hash)
);
CREATE INDEX ix_token_portal_pendencia ON token_portal (org_id, pendencia_id);
