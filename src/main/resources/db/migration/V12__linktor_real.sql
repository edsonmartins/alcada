-- ============================================================================
-- V12 — Integração real do Linktor (ADR-0021, ADR-0025)
-- Nova trilha COMUNICACAO_IMPOSSIVEL (não havia canal) — separada de
-- FALHA_COMUNICACAO (havia canal, tentou e falhou) desde a CHECK, para o radar
-- (009) contar as duas coisas diferentes sem somá-las.
-- ============================================================================

ALTER TABLE trilha DROP CONSTRAINT trilha_tipo_valido;

ALTER TABLE trilha ADD CONSTRAINT trilha_tipo_valido CHECK (tipo IN (
    -- Captura
    'CAPTADA', 'FUNDIDA', 'DESFUNDIDA', 'ROTEADA_POR_REGRA',
    -- Triagem
    'RESOLVIDA', 'REPASSADA', 'RESERVADA', 'REPOUSADA', 'ADIADA', 'DESPERTADA',
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

-- Mapeia o canal do Linktor (channelId do webhook) para a fonte declarada
ALTER TABLE fonte ADD COLUMN linktor_channel_id text;
CREATE INDEX ix_fonte_linktor_channel ON fonte (linktor_channel_id) WHERE linktor_channel_id IS NOT NULL;
