-- ============================================================================
-- V19 — Descarte de 1 toque na triagem realimenta o filtro (001)
-- Acrescenta DESCARTADA ao vocabulário da trilha e cria o sinal aprendido:
-- descartar um item manualmente ensina a captura a marcar (não dropar) futuras
-- capturas do mesmo remetente como baixa confiança ("rever").
-- ============================================================================
ALTER TABLE trilha DROP CONSTRAINT trilha_tipo_valido;

ALTER TABLE trilha ADD CONSTRAINT trilha_tipo_valido CHECK (tipo IN (
    -- Captura
    'CAPTADA', 'FUNDIDA', 'DESFUNDIDA', 'ROTEADA_POR_REGRA',
    -- Triagem
    'RESOLVIDA', 'REPASSADA', 'RESERVADA', 'REPOUSADA', 'ADIADA', 'DESPERTADA', 'DESCARTADA',
    -- Autonomia
    'PROPOSTA_REGISTRADA', 'JANELA_INICIADA', 'EXECUTADA', 'EXECUTADA_POR_AUSENCIA',
    'DESFEITA_NA_JANELA', 'INTERROMPIDA', 'ESCALADA', 'CONVERTIDA_POR_AUSENCIA',
    'NIVEL_PROMOVIDO', 'DEVOLVIDA_PELO_EXECUTOR',
    -- Bloco
    'BLOCO_AGENDADO', 'DOSSIE_MONTADO', 'DECIDIDA_NO_BLOCO',
    -- Comunicação
    'COMUNICADA', 'FALHA_COMUNICACAO', 'COMUNICACAO_IMPOSSIVEL',
    -- Assistente
    'SUGESTAO_EMITIDA', 'SUGESTAO_ACEITA', 'SUGESTAO_RECUSADA', 'SUGESTAO_SILENCIADA',
    -- Correção
    'COMPENSACAO'
));

-- Sinal aprendido: cada descarte manual, por remetente (origem_destino).
CREATE TABLE sinal_descarte (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       uuid        NOT NULL,
    chave        text        NOT NULL,   -- remetente descartado (origem_destino)
    pendencia_id uuid,
    criada_em    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_sinal_descarte ON sinal_descarte (org_id, chave);
