-- ============================================================================
-- V5 — Captura multicanal (RFC-0001, ADR-0005/06/07/11/21)
-- Todas as tabelas carregam org_id (INV-15) — refina o design do 001, que o
-- omitia em evento_bruto/cobranca.
-- ============================================================================

-- Fonte declarada de captura (ADR-0011: nada de varredura completa)
CREATE TABLE fonte (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        uuid        NOT NULL,
    tipo          text        NOT NULL CHECK (tipo IN ('WHATSAPP', 'EMAIL', 'WEBHOOK')),
    identificador text        NOT NULL,               -- grupo, endereço, nome do sistema
    finalidade    text,
    responsavel_id uuid,
    ativa         boolean     NOT NULL DEFAULT true,
    segredo       text        NOT NULL,               -- autenticação do webhook por fonte
    criada_em     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_fonte_org ON fonte (org_id);

-- Bruto: retenção curta com expurgo por expira_em (ADR-0011). O conteúdo bruto
-- de canal vive no Linktor; aqui guardamos o mínimo referenciado por mensagem_id.
CREATE TABLE evento_bruto (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        uuid        NOT NULL,
    fonte_id      uuid        NOT NULL REFERENCES fonte(id),
    mensagem_id   text        NOT NULL,               -- referência ao bruto no Linktor
    autor_ext     text,
    texto         text,                               -- trecho novo já isolado (ADR-0021)
    anexos_ref    text,
    thread_ref    text,
    recebido_em   timestamptz NOT NULL DEFAULT now(),
    expira_em     timestamptz NOT NULL,               -- expurgo obrigatório
    CONSTRAINT evento_bruto_mensagem_unica UNIQUE (fonte_id, mensagem_id)  -- idempotência
);
CREATE INDEX ix_evento_bruto_expira ON evento_bruto (expira_em);
CREATE INDEX ix_evento_bruto_org ON evento_bruto (org_id);

-- Entidade do tenant + apelidos (resolvedor)
CREATE TABLE entidade (
    id             uuid       PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id         uuid       NOT NULL,
    tipo           text       NOT NULL,               -- PESSOA | EMPRESA | PROJETO
    nome_canonico  text       NOT NULL,
    apelidos       text[]     NOT NULL DEFAULT '{}'
);
CREATE INDEX ix_entidade_org ON entidade (org_id);

-- Regra de autonomia (fatia mínima para o roteamento do 001; gestão em 008)
CREATE TABLE regra_autonomia (
    id        uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id    uuid    NOT NULL,
    classe    text    NOT NULL,                       -- DECISAO | BLOQUEIO | ESTEIRA
    nivel     text    NOT NULL CHECK (nivel IN ('N1', 'N2', 'N3')),
    dono_id   uuid    NOT NULL,                       -- dono técnico da delegação automática
    ativa     boolean NOT NULL DEFAULT true,
    criada_em timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_regra_org ON regra_autonomia (org_id, classe) WHERE ativa;

-- Pendência (raiz da fila única)
CREATE TABLE pendencia (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          uuid        NOT NULL,
    titulo          text        NOT NULL,
    quem_espera     text,
    o_que_trava     text,
    prazo_implicito timestamptz,
    valor_em_jogo   numeric(14,2),
    classe          text        NOT NULL CHECK (classe IN ('DECISAO', 'BLOQUEIO', 'ESTEIRA')),
    horizonte       text        NOT NULL DEFAULT 'SEMANA' CHECK (horizonte IN ('HOJE', 'SEMANA', 'TRIMESTRE')),
    status          text        NOT NULL DEFAULT 'ENTRADA'
                                CHECK (status IN ('ENTRADA', 'DELEGADA', 'AGENDADA', 'DORMINDO', 'FECHADA')),
    entidade_id     uuid,                             -- entidade resolvida (para dedup)
    confianca       numeric(4,3),                     -- null = extração pendente
    baixa_confianca boolean     NOT NULL DEFAULT false,
    temperatura     int         NOT NULL DEFAULT 0,
    adiado_count    int         NOT NULL DEFAULT 0,
    criada_em       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_pendencia_fila ON pendencia (org_id, status, criada_em);
CREATE INDEX ix_pendencia_dedup ON pendencia (org_id, entidade_id, criada_em)
    WHERE status <> 'FECHADA';

-- Cobrança deduplicada (temperatura)
CREATE TABLE cobranca (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          uuid        NOT NULL,
    pendencia_id    uuid        NOT NULL REFERENCES pendencia(id),
    evento_bruto_id uuid        NOT NULL REFERENCES evento_bruto(id),
    recebida_em     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_cobranca_pendencia ON cobranca (org_id, pendencia_id);
