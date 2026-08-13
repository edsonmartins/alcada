-- 028: evidências do piloto são append-only e escopadas por organização.
CREATE TABLE intervencao_n2_motivo (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id         uuid NOT NULL,
    delegacao_id   uuid NOT NULL,
    motivo         text CHECK (motivo IN ('DISCORDOU','RISCO_ALTO','PRAZO_INADEQUADO',
                                          'PROPOSTA_INCOMPLETA','NAO_CONFIA_NO_SILENCIO','OUTRO')),
    observacao     text,
    registrado_por uuid NOT NULL,
    registrado_em timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_intervencao_piloto ON intervencao_n2_motivo (org_id, registrado_em);

CREATE TABLE reconciliacao_piloto (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                uuid NOT NULL,
    semana                date NOT NULL,
    decisoes_fora_da_fila integer NOT NULL CHECK (decisoes_fora_da_fila >= 0),
    observacao            text,
    registrado_por        uuid NOT NULL,
    registrado_em         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_reconciliacao_piloto ON reconciliacao_piloto (org_id, semana);

ALTER TABLE descarte_captura ADD COLUMN evento_bruto_id uuid;

CREATE TABLE avaliacao_descarte_piloto (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id         uuid NOT NULL,
    descarte_id    uuid NOT NULL,
    resultado      text NOT NULL CHECK (resultado IN ('ERA_PENDENCIA','NAO_ERA','INCONCLUSIVO')),
    avaliado_por   uuid NOT NULL,
    avaliado_em    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_avaliacao_descarte_piloto ON avaliacao_descarte_piloto (org_id, avaliado_em);
