-- P034 — revisão como sessão de redução (ADR-0033/RFC-0014).
CREATE TABLE sessao_revisao (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id uuid NOT NULL,
    gestor_id uuid NOT NULL,
    status text NOT NULL DEFAULT 'ABERTA' CHECK (status IN ('ABERTA','CONCLUIDA')),
    iniciada_em timestamptz NOT NULL DEFAULT now(),
    concluida_em timestamptz,
    resumo jsonb
);

CREATE UNIQUE INDEX ux_sessao_revisao_aberta
    ON sessao_revisao(org_id,gestor_id) WHERE status='ABERTA';
CREATE INDEX ix_sessao_revisao_org ON sessao_revisao(org_id,iniciada_em DESC);

CREATE TABLE sessao_revisao_dependencia (
    org_id uuid NOT NULL,
    sessao_id uuid NOT NULL REFERENCES sessao_revisao(id),
    pendencia_id uuid NOT NULL,
    PRIMARY KEY(org_id,sessao_id,pendencia_id)
);
CREATE INDEX ix_sessao_revisao_dependencia_item
    ON sessao_revisao_dependencia(org_id,pendencia_id);

CREATE TABLE protecao_agenda_revisao (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id uuid NOT NULL,
    sessao_id uuid NOT NULL REFERENCES sessao_revisao(id),
    gestor_id uuid NOT NULL,
    pendencia_ref uuid NOT NULL,
    inicio timestamptz NOT NULL,
    duracao_minutos integer NOT NULL CHECK (duracao_minutos BETWEEN 30 AND 480),
    status text NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE','AGENDADA','CANCELADA','FALHA')),
    evento_calendario_id text,
    criada_em timestamptz NOT NULL DEFAULT now(),
    UNIQUE(org_id,sessao_id)
);

-- Desfecho explícito "observar" não ativa nem silencia a regra.
CREATE TABLE observacao_proposta_regra (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id uuid NOT NULL,
    classe text NOT NULL CHECK (classe IN ('DECISAO','BLOQUEIO','ESTEIRA')),
    por uuid NOT NULL,
    observada_em timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_observacao_proposta_regra_org
    ON observacao_proposta_regra(org_id,classe,observada_em DESC);

ALTER TABLE trilha DROP CONSTRAINT trilha_tipo_valido;
ALTER TABLE trilha ADD CONSTRAINT trilha_tipo_valido CHECK (tipo IN (
    'CAPTADA','FUNDIDA','DESFUNDIDA','ROTEADA_POR_REGRA',
    'RESOLVIDA','REPASSADA','RESERVADA','REPOUSADA','ADIADA','DESPERTADA','DESCARTADA',
    'PEDIDO_INFORMACAO_CRIADO','PEDIDO_INFORMACAO_RESPONDIDO','PEDIDO_INFORMACAO_VENCIDO',
    'LEMBRETE_CRIADO','COMPROMISSO_AGENDADO','FALHA_COMPROMISSO',
    'PROPOSTA_REGISTRADA','JANELA_INICIADA','EXECUTADA','EXECUTADA_POR_AUSENCIA',
    'DESFEITA_NA_JANELA','INTERROMPIDA','ESCALADA','CONVERTIDA_POR_AUSENCIA',
    'NIVEL_PROMOVIDO','DEVOLVIDA_PELO_EXECUTOR','RETORNO_RECEBIDO',
    'BLOCO_AGENDADO','DOSSIE_MONTADO','DECIDIDA_NO_BLOCO',
    'COMUNICADA','FALHA_COMUNICACAO','COMUNICACAO_IMPOSSIVEL',
    'SUGESTAO_EMITIDA','SUGESTAO_ACEITA','SUGESTAO_RECUSADA','SUGESTAO_SILENCIADA',
    'SUGESTAO_OBSERVADA','COMPENSACAO'
));
