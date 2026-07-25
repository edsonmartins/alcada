-- ============================================================================
-- V3 — Scheduler persistente (CLAUDE.md §4)
-- Nenhum timer em memória; estado na tabela. Claim por lock (SKIP LOCKED),
-- retry exponencial. Reinício da aplicação recupera jobs sem perder nem
-- duplicar (chave de idempotência conforme RFC-0002).
-- ============================================================================
CREATE TABLE job (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id            uuid        NOT NULL,                       -- INV-15
    tipo              text        NOT NULL,                       -- ex.: VIRADA_JANELA, ESCALONAMENTO, DESPERTAR
    chave             text        NOT NULL,                       -- idempotência (delegacao_id,transicao)|(pendencia_id,transicao,ocorrencia)
    payload           jsonb       NOT NULL DEFAULT '{}'::jsonb,
    executar_em       timestamptz NOT NULL,
    status            text        NOT NULL DEFAULT 'AGENDADO'
                                  CHECK (status IN ('AGENDADO', 'EXECUTANDO', 'CONCLUIDO', 'ERRO')),
    tentativas        int         NOT NULL DEFAULT 0,
    proxima_tentativa timestamptz,
    lock_por          text,                                       -- identificador do worker que reservou
    lock_em           timestamptz,
    criado_em         timestamptz NOT NULL DEFAULT now(),
    concluido_em      timestamptz,
    ultimo_erro       text,

    -- Um job por (tipo,chave): garante exatamente-uma-vez lógica na virada
    CONSTRAINT job_idempotente UNIQUE (tipo, chave)
);

-- Claim do worker: agendados cujo horário chegou, ainda não reservados
CREATE INDEX ix_job_claim
    ON job (executar_em)
    WHERE status = 'AGENDADO';
