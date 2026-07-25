-- ============================================================================
-- V2 — Outbox transacional (CLAUDE.md §4, INV-14)
-- Transição de estado e escrita no outbox na MESMA transação.
-- Entrega ao menos uma vez; idempotência por chave única.
-- Worker consome com SELECT ... FOR UPDATE SKIP LOCKED.
-- ============================================================================
CREATE TABLE outbox (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id            uuid        NOT NULL,                       -- INV-15
    tipo              text        NOT NULL,                       -- nome do evento (docs/API.md §Eventos)
    payload           jsonb       NOT NULL,
    idempotency_key   text        NOT NULL,                       -- (pendencia_id,transicao) ou Idempotency-Key
    status            text        NOT NULL DEFAULT 'PENDENTE'
                                  CHECK (status IN ('PENDENTE', 'ENVIADO', 'ERRO')),
    tentativas        int         NOT NULL DEFAULT 0,
    disponivel_em     timestamptz NOT NULL DEFAULT now(),         -- backoff: adia a próxima tentativa
    criado_em         timestamptz NOT NULL DEFAULT now(),
    enviado_em        timestamptz,
    ultimo_erro       text,

    CONSTRAINT outbox_idempotente UNIQUE (idempotency_key)
);

-- Varredura do worker: pendentes já disponíveis, mais antigos primeiro
CREATE INDEX ix_outbox_claim
    ON outbox (disponivel_em)
    WHERE status = 'PENDENTE';
