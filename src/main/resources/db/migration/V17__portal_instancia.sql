-- ============================================================================
-- V17 — Portal de instância + autoavaliação (pacote 015, RFC-0006 / ADR-0013)
-- Link assinado sem login para a contraparte externa ver o estado e declarar
-- conformidade. O banco guarda só o HASH do token (vazar o banco não vaza links).
-- ============================================================================
CREATE TABLE token_instancia (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       uuid        NOT NULL,                     -- INV-15
    instancia_id uuid        NOT NULL REFERENCES instancia(id),
    token_hash   text        NOT NULL,                     -- SHA-256 do token; nunca o cru
    expira_em    timestamptz NOT NULL,
    revogado     boolean     NOT NULL DEFAULT false,
    criado_em    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT token_instancia_hash_unico UNIQUE (token_hash)
);
CREATE INDEX ix_token_instancia ON token_instancia (org_id, instancia_id);

-- Declaração de conformidade da contraparte (informa o gestor; não decide — INV-10).
CREATE TABLE autoavaliacao (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        uuid        NOT NULL,
    instancia_id  uuid        NOT NULL REFERENCES instancia(id),
    criterio_chave text       NOT NULL,
    conforme      boolean     NOT NULL,
    declarado_em  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_autoavaliacao ON autoavaliacao (org_id, instancia_id);
