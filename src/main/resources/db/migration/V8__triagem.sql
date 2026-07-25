-- ============================================================================
-- V8 — Triagem (ADR-0002, ADR-0018)
-- Adiamento de primeira classe + colunas de agendamento/adormecimento na fila.
-- ============================================================================

-- Adiamento (ADR-0002): data obrigatória + o_que_falta declarado.
CREATE TABLE adiamento (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       uuid        NOT NULL,                       -- INV-15
    pendencia_id uuid        NOT NULL REFERENCES pendencia(id),
    volta_em     timestamptz NOT NULL,
    o_que_falta  text        NOT NULL CHECK (o_que_falta IN ('NADA', 'INSUMO', 'TERCEIRO')),
    ocorrencia   int         NOT NULL,                       -- contador do adormecimento (RFC-0002)
    criado_em    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_adiamento_pendencia ON adiamento (org_id, pendencia_id);

-- Fila ganha estados de agendamento/adormecimento
ALTER TABLE pendencia ADD COLUMN agendado_para timestamptz;   -- reservar → AGENDADA
ALTER TABLE pendencia ADD COLUMN volta_em      timestamptz;   -- repousar/adiar
ALTER TABLE pendencia ADD COLUMN ocorrencia    int NOT NULL DEFAULT 0;  -- despertares
