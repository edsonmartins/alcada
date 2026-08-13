CREATE TABLE pedido_informacao (
    id uuid PRIMARY KEY, org_id uuid NOT NULL, pendencia_id uuid NOT NULL,
    contato_id uuid NOT NULL REFERENCES contato_externo(id),
    pergunta text NOT NULL CHECK (length(trim(pergunta)) BETWEEN 3 AND 1000),
    prazo timestamptz NOT NULL,
    estado text NOT NULL DEFAULT 'AGUARDANDO_ENVIO'
        CHECK (estado IN ('AGUARDANDO_ENVIO','AGUARDANDO_RESPOSTA','RESPONDIDO','CANCELADO','VENCIDO')),
    criada_em timestamptz NOT NULL DEFAULT now(), respondida_em timestamptz, vencida_em timestamptz
);
CREATE UNIQUE INDEX ux_pedido_informacao_aberto ON pedido_informacao (org_id, pendencia_id)
    WHERE estado IN ('AGUARDANDO_ENVIO','AGUARDANDO_RESPOSTA');
CREATE INDEX ix_pedido_informacao_prazo ON pedido_informacao (org_id, prazo, estado);

ALTER TABLE correlacao_retorno ALTER COLUMN delegacao_id DROP NOT NULL;
ALTER TABLE correlacao_retorno ADD COLUMN pedido_informacao_id uuid;
ALTER TABLE correlacao_retorno DROP CONSTRAINT correlacao_retorno_org_id_delegacao_id_key;
ALTER TABLE correlacao_retorno ADD CONSTRAINT correlacao_retorno_alvo_ck
    CHECK ((delegacao_id IS NOT NULL) <> (pedido_informacao_id IS NOT NULL));
CREATE UNIQUE INDEX ux_correlacao_retorno_delegacao ON correlacao_retorno (org_id, delegacao_id)
    WHERE delegacao_id IS NOT NULL;
CREATE UNIQUE INDEX ux_correlacao_retorno_pedido ON correlacao_retorno (org_id, pedido_informacao_id)
    WHERE pedido_informacao_id IS NOT NULL;

ALTER TABLE retorno_delegacao ALTER COLUMN delegacao_id DROP NOT NULL;
ALTER TABLE retorno_delegacao ADD COLUMN pedido_informacao_id uuid;
ALTER TABLE retorno_delegacao ADD CONSTRAINT retorno_delegacao_alvo_ck
    CHECK ((delegacao_id IS NOT NULL) <> (pedido_informacao_id IS NOT NULL));

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
    'SUGESTAO_EMITIDA','SUGESTAO_ACEITA','SUGESTAO_RECUSADA','SUGESTAO_SILENCIADA','COMPENSACAO'
));
