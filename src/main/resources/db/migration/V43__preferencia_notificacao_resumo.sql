CREATE TABLE preferencia_notificacao (
    org_id uuid NOT NULL,
    gestor_id uuid NOT NULL,
    canal text NOT NULL DEFAULT 'EMAIL' CHECK (canal IN ('EMAIL')),
    resumo_inicio time,
    resumo_fim time,
    ativa boolean NOT NULL DEFAULT true,
    atualizada_em timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, gestor_id)
);
ALTER TABLE delegacao ADD COLUMN gestor_id uuid;
ALTER TABLE pedido_informacao ADD COLUMN gestor_id uuid;
