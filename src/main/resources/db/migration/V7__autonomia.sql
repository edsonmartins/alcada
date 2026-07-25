-- ============================================================================
-- V7 — Motor de autonomia N2 (RFC-0002, ADR-0003/04/16)
-- Reusa `job` (scheduler) e `outbox` do bootstrap. Novas tabelas com org_id.
-- ============================================================================

-- Parâmetros por classe (fatia ampla: DECISAO/BLOQUEIO/ESTEIRA por org).
-- Ausência de linha → defaults no código (janela PT4H, escalonamento PT24H).
CREATE TABLE classe_decisao (
    id            uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        uuid    NOT NULL,
    classe        text    NOT NULL CHECK (classe IN ('DECISAO', 'BLOQUEIO', 'ESTEIRA')),
    nivel_padrao  text    CHECK (nivel_padrao IN ('N1', 'N2', 'N3')),
    nivel_maximo  text    NOT NULL DEFAULT 'N1' CHECK (nivel_maximo IN ('N1', 'N2', 'N3')),
    valor_limite  numeric(14,2),
    janela_reversibilidade interval NOT NULL DEFAULT interval '4 hours',
    escalonamento interval NOT NULL DEFAULT interval '24 hours',
    elegivel_n1   boolean NOT NULL DEFAULT true,
    CONSTRAINT classe_decisao_unica UNIQUE (org_id, classe)
);

-- Ausência do gestor (férias/licença): converte N2 → N3 automaticamente.
CREATE TABLE ausencia (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id     uuid        NOT NULL,
    pessoa_id  uuid        NOT NULL,
    de         timestamptz NOT NULL,
    ate        timestamptz NOT NULL
);
CREATE INDEX ix_ausencia_vigente ON ausencia (org_id, pessoa_id, de, ate);

-- Delegação: o contrato de autonomia daquele repasse (a janela pertence a ele).
CREATE TABLE delegacao (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       uuid        NOT NULL,
    pendencia_id uuid        NOT NULL,
    dono_id      uuid        NOT NULL,
    nivel        text        NOT NULL CHECK (nivel IN ('N1', 'N2', 'N3')),
    prazo        timestamptz NOT NULL,
    janela       interval    NOT NULL,
    escalonamento interval   NOT NULL,
    status       text        NOT NULL DEFAULT 'ABERTA'
                             CHECK (status IN ('ABERTA', 'PROPOSTA', 'AGUARDANDO_JANELA',
                                               'EXECUTADA', 'DEVOLVIDA', 'ESCALADA')),
    proposta     text,
    proposta_em  timestamptz,
    criada_em    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_delegacao_pendencia ON delegacao (org_id, pendencia_id);
CREATE INDEX ix_delegacao_dono ON delegacao (org_id, dono_id, status);
