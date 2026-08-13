CREATE TABLE resumo_diario (
    id uuid PRIMARY KEY,
    org_id uuid NOT NULL,
    gestor_id uuid NOT NULL,
    periodo text NOT NULL CHECK (periodo IN ('INICIO','FIM')),
    data_local date NOT NULL,
    retrato jsonb NOT NULL,
    total_itens integer NOT NULL CHECK (total_itens >= 0),
    estimativa_minutos integer,
    criado_em timestamptz NOT NULL DEFAULT now(),
    UNIQUE (org_id, gestor_id, periodo, data_local)
);
CREATE INDEX ix_resumo_diario_gestor ON resumo_diario (org_id, gestor_id, criado_em DESC);

ALTER TABLE retorno_delegacao ADD COLUMN decisao_chave text;
ALTER TABLE retorno_delegacao ADD COLUMN avaliado_por uuid;
ALTER TABLE retorno_delegacao ADD COLUMN avaliado_em timestamptz;
CREATE UNIQUE INDEX ux_retorno_decisao_chave ON retorno_delegacao (org_id, decisao_chave)
    WHERE decisao_chave IS NOT NULL;

ALTER TABLE trilha DROP CONSTRAINT trilha_tipo_valido;
ALTER TABLE trilha ADD CONSTRAINT trilha_tipo_valido CHECK (tipo IN (
    'CAPTADA','FUNDIDA','DESFUNDIDA','ROTEADA_POR_REGRA',
    'RESOLVIDA','REPASSADA','RESERVADA','REPOUSADA','ADIADA','DESPERTADA','DESCARTADA',
    'PEDIDO_INFORMACAO_CRIADO','PEDIDO_INFORMACAO_RESPONDIDO','PEDIDO_INFORMACAO_VENCIDO',
    'LEMBRETE_CRIADO','COMPROMISSO_AGENDADO','FALHA_COMPROMISSO',
    'PROPOSTA_REGISTRADA','JANELA_INICIADA','EXECUTADA','EXECUTADA_POR_AUSENCIA',
    'DESFEITA_NA_JANELA','INTERROMPIDA','ESCALADA','CONVERTIDA_POR_AUSENCIA',
    'NIVEL_PROMOVIDO','DEVOLVIDA_PELO_EXECUTOR','RETORNO_RECEBIDO','RETORNO_AVALIADO',
    'BLOCO_AGENDADO','DOSSIE_MONTADO','DECIDIDA_NO_BLOCO',
    'COMUNICADA','FALHA_COMUNICACAO','COMUNICACAO_IMPOSSIVEL',
    'SUGESTAO_EMITIDA','SUGESTAO_ACEITA','SUGESTAO_RECUSADA','SUGESTAO_SILENCIADA','SUGESTAO_OBSERVADA',
    'COMPENSACAO'
));
