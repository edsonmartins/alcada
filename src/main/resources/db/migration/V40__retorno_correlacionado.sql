CREATE TABLE correlacao_retorno (
    id uuid PRIMARY KEY, org_id uuid NOT NULL, delegacao_id uuid NOT NULL,
    token_hash bytea NOT NULL, canal text NOT NULL, destino_hash bytea NOT NULL,
    criada_em timestamptz NOT NULL DEFAULT now(), expira_em timestamptz NOT NULL,
    revogada_em timestamptz, UNIQUE (org_id, token_hash), UNIQUE (org_id, delegacao_id)
);
CREATE INDEX ix_correlacao_retorno_delegacao ON correlacao_retorno (org_id, delegacao_id);
CREATE TABLE retorno_delegacao (
    id uuid PRIMARY KEY, org_id uuid NOT NULL, delegacao_id uuid NOT NULL,
    mensagem_id_hash bytea NOT NULL,
    tipo text NOT NULL DEFAULT 'INCONCLUSIVO' CHECK (tipo IN ('INFORMACAO','PROPOSTA','RESULTADO',
        'COBRANCA','CONTESTACAO','PEDIDO_PRAZO','SEM_EFEITO','INCONCLUSIVO')),
    trecho_minimizado text NOT NULL,
    estado text NOT NULL DEFAULT 'OBSERVADO' CHECK (estado IN ('OBSERVADO','APLICADO','REJEITADO')),
    recebido_em timestamptz NOT NULL DEFAULT now(), UNIQUE (org_id, mensagem_id_hash)
);
CREATE INDEX ix_retorno_delegacao ON retorno_delegacao (org_id, delegacao_id, recebido_em);
ALTER TABLE delegacao ADD COLUMN retorno_pendente boolean NOT NULL DEFAULT false;

ALTER TABLE trilha DROP CONSTRAINT trilha_tipo_valido;
ALTER TABLE trilha ADD CONSTRAINT trilha_tipo_valido CHECK (tipo IN (
    'CAPTADA','FUNDIDA','DESFUNDIDA','ROTEADA_POR_REGRA',
    'RESOLVIDA','REPASSADA','RESERVADA','REPOUSADA','ADIADA','DESPERTADA','DESCARTADA',
    'LEMBRETE_CRIADO','COMPROMISSO_AGENDADO','FALHA_COMPROMISSO',
    'PROPOSTA_REGISTRADA','JANELA_INICIADA','EXECUTADA','EXECUTADA_POR_AUSENCIA',
    'DESFEITA_NA_JANELA','INTERROMPIDA','ESCALADA','CONVERTIDA_POR_AUSENCIA',
    'NIVEL_PROMOVIDO','DEVOLVIDA_PELO_EXECUTOR','RETORNO_RECEBIDO',
    'BLOCO_AGENDADO','DOSSIE_MONTADO','DECIDIDA_NO_BLOCO',
    'COMUNICADA','FALHA_COMUNICACAO','COMUNICACAO_IMPOSSIVEL',
    'SUGESTAO_EMITIDA','SUGESTAO_ACEITA','SUGESTAO_RECUSADA','SUGESTAO_SILENCIADA','COMPENSACAO'
));
