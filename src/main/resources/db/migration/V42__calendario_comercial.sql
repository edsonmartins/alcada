CREATE TABLE calendario_comercial (
    org_id uuid PRIMARY KEY,
    timezone text NOT NULL,
    dias_uteis text NOT NULL DEFAULT '1,2,3,4,5',
    inicio time NOT NULL DEFAULT time '09:00',
    fim time NOT NULL DEFAULT time '18:00',
    atualizado_em timestamptz NOT NULL DEFAULT now(),
    CHECK (inicio < fim)
);
CREATE TABLE feriado_comercial (
    org_id uuid NOT NULL,
    data date NOT NULL,
    nome text NOT NULL,
    PRIMARY KEY (org_id, data)
);
