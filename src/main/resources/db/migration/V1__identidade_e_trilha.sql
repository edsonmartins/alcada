-- ============================================================================
-- V1 — Identidade mínima + Trilha imutável
-- Fontes: CONSTITUTION INV-11/INV-15 · ADR-0016 (anexo normativo) · ADR-0023
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Organização (raiz do tenant) e Pessoa
-- ---------------------------------------------------------------------------
CREATE TABLE organizacao (
    id          uuid        PRIMARY KEY,
    nome        text        NOT NULL,
    sku         text        NOT NULL DEFAULT 'CLOUD'   -- CLOUD | SOBERANO (ADR-0020)
                            CHECK (sku IN ('CLOUD', 'SOBERANO')),
    criada_em   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE pessoa (
    id          uuid        PRIMARY KEY,
    org_id      uuid        NOT NULL REFERENCES organizacao(id),   -- INV-15
    nome        text        NOT NULL,
    email       text,
    criada_em   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_pessoa_org ON pessoa(org_id);

-- ---------------------------------------------------------------------------
-- Trilha — registro append-only, prova (INV-11, ADR-0016)
--
-- Particionada por mês em `ocorrido_em` (RANGE). A chave primária inclui a
-- coluna de partição, exigência do Postgres para tabela particionada.
-- O vocabulário de `tipo` é FECHADO pelo anexo normativo do ADR-0016:
-- nenhum módulo grava valor fora desta lista sem emenda ao ADR.
-- ---------------------------------------------------------------------------
CREATE TABLE trilha (
    id                uuid        NOT NULL DEFAULT gen_random_uuid(),
    org_id            uuid        NOT NULL,                         -- INV-15
    pendencia_id      uuid        NOT NULL,
    tipo              text        NOT NULL,
    ator              text        NOT NULL,                         -- formato validado abaixo
    ocorrido_em       timestamptz NOT NULL DEFAULT now(),
    origem            jsonb,                                        -- canal, fonte, mensagem_id
    estado_anterior   text,
    estado_posterior  text,
    carga             jsonb,                                        -- campos extras por tipo de evento

    PRIMARY KEY (id, ocorrido_em),

    -- Vocabulário fechado — anexo normativo ADR-0016 (29 tipos)
    CONSTRAINT trilha_tipo_valido CHECK (tipo IN (
        -- Captura
        'CAPTADA', 'FUNDIDA', 'DESFUNDIDA', 'ROTEADA_POR_REGRA',
        -- Triagem
        'RESOLVIDA', 'REPASSADA', 'RESERVADA', 'REPOUSADA', 'ADIADA', 'DESPERTADA',
        -- Autonomia
        'PROPOSTA_REGISTRADA', 'JANELA_INICIADA', 'EXECUTADA', 'EXECUTADA_POR_AUSENCIA',
        'DESFEITA_NA_JANELA', 'INTERROMPIDA', 'ESCALADA', 'CONVERTIDA_POR_AUSENCIA',
        'NIVEL_PROMOVIDO',
        -- Bloco
        'BLOCO_AGENDADO', 'DOSSIE_MONTADO', 'DECIDIDA_NO_BLOCO',
        -- Comunicação
        'COMUNICADA', 'FALHA_COMUNICACAO',
        -- Assistente
        'SUGESTAO_EMITIDA', 'SUGESTAO_ACEITA', 'SUGESTAO_RECUSADA', 'SUGESTAO_SILENCIADA',
        -- Correção
        'COMPENSACAO'
    )),

    -- Formato do ator (anexo ADR-0016):
    --   HUMANO:{pessoa_id} | SISTEMA:{motor|regra}:{ident} | ASSISTENTE:{modelo}@{versao}
    CONSTRAINT trilha_ator_valido CHECK (
        ator ~ '^HUMANO:.+$'
        OR ator ~ '^SISTEMA:(motor|regra):.+$'
        OR ator ~ '^ASSISTENTE:.+@.+$'
    )
) PARTITION BY RANGE (ocorrido_em);

CREATE INDEX ix_trilha_pendencia ON trilha(pendencia_id, ocorrido_em);
CREATE INDEX ix_trilha_org ON trilha(org_id, ocorrido_em);

-- ---------------------------------------------------------------------------
-- Append-only garantido em DOIS níveis (INV-11):
--   1) trigger que rejeita UPDATE/DELETE mesmo para superusuário
--   2) REVOKE de UPDATE/DELETE do role da aplicação (defesa em profundidade)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trilha_bloqueia_mutacao()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'trilha e append-only (INV-11): % proibido', TG_OP
        USING ERRCODE = 'raise_exception';
END;
$$;

-- Trigger no pai particionado propaga às partições (PG 13+)
CREATE TRIGGER trg_trilha_append_only
    BEFORE UPDATE OR DELETE ON trilha
    FOR EACH ROW EXECUTE FUNCTION trilha_bloqueia_mutacao();

REVOKE UPDATE, DELETE, TRUNCATE ON trilha FROM alcada;

-- ---------------------------------------------------------------------------
-- Partições mensais. Rolagem futura vira job do scheduler (pacote 004).
-- Aqui: função utilitária + janela inicial (mês corrente até +5) + DEFAULT
-- para nunca falhar um INSERT fora da janela pré-criada.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trilha_cria_particao(mes date)
RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    ini date := date_trunc('month', mes)::date;
    fim date := (date_trunc('month', mes) + interval '1 month')::date;
    nome text := 'trilha_' || to_char(ini, 'YYYY_MM');
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = nome) THEN
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF trilha FOR VALUES FROM (%L) TO (%L)',
            nome, ini, fim);
    END IF;
END;
$$;

-- Partição DEFAULT: destino de qualquer linha fora das partições explícitas
CREATE TABLE trilha_default PARTITION OF trilha DEFAULT;

-- Janela inicial de 6 meses a partir do mês corrente
DO $$
DECLARE
    m date := date_trunc('month', now())::date;
    i int;
BEGIN
    FOR i IN 0..5 LOOP
        PERFORM trilha_cria_particao((m + (i || ' month')::interval)::date);
    END LOOP;
END;
$$;
