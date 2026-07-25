-- ============================================================================
-- V15 — Agregado Esteira (pacote 012, ADR-0012 / RFC-0006)
-- Esteira → Etapa[]; Instancia (passagem de entidade externa); Checklist
-- VERSIONADO por etapa; Criterio {OBJETIVO|JULGAMENTO}; Avaliacao + Apontamentos.
-- Tudo escopado por org_id (INV-15).
-- ============================================================================
CREATE TABLE esteira (
    id        uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id    uuid        NOT NULL,
    nome      text        NOT NULL,
    criada_em timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE etapa (
    id             uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id         uuid    NOT NULL,
    esteira_id     uuid    NOT NULL REFERENCES esteira(id),
    ordem          int     NOT NULL,
    nome           text    NOT NULL,
    dono_id        uuid,
    sla            interval,
    etapa_do_gestor boolean NOT NULL DEFAULT false,
    UNIQUE (org_id, esteira_id, ordem)
);

CREATE TABLE checklist (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id     uuid        NOT NULL,
    etapa_id   uuid        NOT NULL REFERENCES etapa(id),
    versao     int         NOT NULL,
    criada_em  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (org_id, etapa_id, versao)
);

CREATE TABLE criterio (
    id           uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       uuid    NOT NULL,
    checklist_id uuid    NOT NULL REFERENCES checklist(id),
    chave        text    NOT NULL,
    descricao    text    NOT NULL,
    tipo         text    NOT NULL CHECK (tipo IN ('OBJETIVO', 'JULGAMENTO')),
    obrigatorio  boolean NOT NULL DEFAULT true
);

CREATE TABLE instancia (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          uuid        NOT NULL,
    esteira_id      uuid        NOT NULL REFERENCES esteira(id),
    entidade_externa text       NOT NULL,
    etapa_atual_id  uuid        REFERENCES etapa(id),
    status          text        NOT NULL DEFAULT 'EM_ANDAMENTO'
                                CHECK (status IN ('EM_ANDAMENTO', 'CONCLUIDA')),
    entrou_em       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_instancia_etapa ON instancia (org_id, etapa_atual_id);

CREATE TABLE avaliacao (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          uuid        NOT NULL,
    instancia_id    uuid        NOT NULL REFERENCES instancia(id),
    etapa_id        uuid        NOT NULL REFERENCES etapa(id),
    checklist_versao int,
    desfecho        text        NOT NULL CHECK (desfecho IN ('APROVADA', 'REPROVADA', 'PENDENTE_JULGAMENTO')),
    avaliador_id    uuid,
    avaliada_em     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_avaliacao_etapa ON avaliacao (org_id, etapa_id, avaliada_em);

CREATE TABLE apontamento (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       uuid NOT NULL,
    avaliacao_id uuid NOT NULL REFERENCES avaliacao(id),
    texto        text NOT NULL,
    tipo         text NOT NULL CHECK (tipo IN ('OBJETIVO', 'JULGAMENTO'))
);
CREATE INDEX ix_apontamento_avaliacao ON apontamento (org_id, avaliacao_id);
