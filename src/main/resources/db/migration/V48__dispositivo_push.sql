CREATE TABLE dispositivo_push (
    org_id uuid NOT NULL REFERENCES organizacao(id),
    pessoa_id uuid NOT NULL REFERENCES pessoa(id),
    instalacao_id uuid NOT NULL,
    plataforma text NOT NULL CHECK (plataforma IN ('ANDROID','IOS')),
    token_cifrado text NOT NULL,
    token_hash text NOT NULL,
    atualizado_em timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, pessoa_id, instalacao_id),
    UNIQUE (org_id, token_hash)
);
CREATE INDEX ix_dispositivo_push_pessoa ON dispositivo_push(org_id,pessoa_id);
