-- 024 F1b — seleção de grupos (opt-in do gestor; ADR-0011 §1 fonte declarada).
-- O segredo HMAC é por CANAL (fonte). A SELEÇÃO é por GRUPO: cada grupo que o bot
-- vê vira uma linha (descoberta, só metadados: id/nome), mas o conteúdo só é
-- ingerido quando `ativa` = true (o gestor escolheu controlar). Grupo não
-- selecionado é descartado no webhook (nada de evento_bruto).
CREATE TABLE grupo_acompanhado (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id         uuid        NOT NULL,                      -- INV-15
    fonte_id       uuid        NOT NULL REFERENCES fonte(id), -- canal (segredo/HMAC)
    grupo_id       text        NOT NULL,                      -- chat_jid do grupo
    nome           text,                                      -- best-effort (envelope.group.name)
    ativa          boolean     NOT NULL DEFAULT false,        -- opt-in: só acompanha se true
    finalidade     text,                                      -- ADR-0011 §1
    primeiro_visto timestamptz NOT NULL DEFAULT now(),
    ultimo_visto   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (fonte_id, grupo_id)
);

CREATE INDEX ix_grupo_acomp_org ON grupo_acompanhado (org_id);
