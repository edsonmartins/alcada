-- ============================================================================
-- V4 — Gateway de modelos (ADR-0020, RFC-0007)
-- Fila de reprocesso (indisponibilidade não perde a captura) e observabilidade
-- de chamadas. Nenhuma das duas guarda prompt nem resposta — só referência.
-- ============================================================================

-- Fila de reprocesso: indisponibilidade vira erro tratado e a tarefa espera.
-- Guarda SÓ a referência à mensagem (bruto no Linktor); ao reprocessar, a
-- minimização roda de novo — nada sensível é persistido aqui.
CREATE TABLE tarefa_reprocesso (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id            uuid        NOT NULL,                       -- INV-15
    tipo_tarefa       text        NOT NULL,                       -- ex.: extracao
    ref_mensagem_id   uuid        NOT NULL,                       -- referência ao bruto no Linktor
    tentativas        int         NOT NULL DEFAULT 0,
    status            text        NOT NULL DEFAULT 'PENDENTE'
                                  CHECK (status IN ('PENDENTE', 'CONCLUIDO', 'ERRO')),
    disponivel_em     timestamptz NOT NULL DEFAULT now(),
    criado_em         timestamptz NOT NULL DEFAULT now(),
    ultimo_erro       text
);
CREATE INDEX ix_reprocesso_claim
    ON tarefa_reprocesso (disponivel_em)
    WHERE status = 'PENDENTE';

-- Observabilidade por chamada. SEM prompt e SEM resposta: só metadados e a
-- referência `ref_mensagem_id` (ADR-0020 §Observabilidade).
CREATE TABLE chamada_modelo (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id            uuid        NOT NULL,                       -- INV-15
    tarefa            text        NOT NULL,                       -- extracao | redacao | classificacao | embedding
    sensibilidade     text        NOT NULL,                       -- PUBLICA | INTERNA | RESTRITA
    destino           text        NOT NULL,                       -- EXTERNO | LOCAL
    provedor_efetivo  text,
    modelo            text,
    tokens_in         int         NOT NULL DEFAULT 0,
    tokens_out        int         NOT NULL DEFAULT 0,
    latencia_ms       int         NOT NULL DEFAULT 0,
    custo             numeric(12,6) NOT NULL DEFAULT 0,
    schema_ok         boolean,
    ref_mensagem_id   uuid,
    ocorrido_em       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_chamada_org ON chamada_modelo (org_id, ocorrido_em);
