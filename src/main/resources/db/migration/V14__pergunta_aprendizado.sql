-- ============================================================================
-- V14 — Laço de aprendizado (pacote 011, RFC-0003 / ADR-0019)
-- Uma pergunta situada por padrão candidato. Índice parcial garante NO MÁXIMO
-- uma pergunta ABERTA por classe (a disciplina "1 por decisão" do RFC).
-- ============================================================================
CREATE TABLE pergunta_aprendizado (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id         uuid        NOT NULL,
    classe         text        NOT NULL CHECK (classe IN ('DECISAO', 'BLOQUEIO', 'ESTEIRA')),
    pendencia_ref  uuid        NOT NULL,                    -- caso representativo (p/ trilha)
    status         text        NOT NULL DEFAULT 'ABERTA'
                               CHECK (status IN ('ABERTA', 'ACEITA', 'RECUSADA', 'SILENCIADA')),
    criada_em      timestamptz NOT NULL DEFAULT now(),
    respondida_em  timestamptz,
    respondida_por uuid
);

-- No máximo uma pergunta ABERTA por classe (org).
CREATE UNIQUE INDEX ux_pergunta_aberta ON pergunta_aprendizado (org_id, classe) WHERE status = 'ABERTA';
CREATE INDEX ix_pergunta_org ON pergunta_aprendizado (org_id, criada_em);
