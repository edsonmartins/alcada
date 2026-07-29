-- 024 F1 — log auditável da proporção processada por fonte (ADR-0011 §3 / CLAUDE.md §4:
-- "captura seletiva; nunca varredura completa; log auditável da proporção processada").
-- Cada janela de grupo avaliada incrementa `janelas_vistas`; só as que o pré-filtro
-- determinístico julga candidatas (e portanto vão ao modelo) incrementam
-- `janelas_processadas`. A proporção processadas/vistas é a evidência de que a
-- captura é seletiva, auditável por organização e por canal.
CREATE TABLE captura_proporcao (
    org_id              uuid        NOT NULL,                       -- INV-15
    fonte_id            uuid        NOT NULL REFERENCES fonte(id),  -- o canal (fonte declarada)
    janelas_vistas      bigint      NOT NULL DEFAULT 0,
    janelas_processadas bigint      NOT NULL DEFAULT 0,
    atualizado_em       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, fonte_id)
);
