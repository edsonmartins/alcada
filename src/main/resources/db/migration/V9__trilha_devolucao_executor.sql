-- ============================================================================
-- V9 — Emenda ao vocabulário da trilha (ADR-0024)
-- Acrescenta DEVOLVIDA_PELO_EXECUTOR ao grupo de autonomia. Recria o CHECK; não
-- toca dados existentes. Não confundir com ESCALADA (silêncio de ambos).
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
    'COMUNICADA', 'FALHA_COMUNICACAO',
    -- Assistente
    'SUGESTAO_EMITIDA', 'SUGESTAO_ACEITA', 'SUGESTAO_RECUSADA', 'SUGESTAO_SILENCIADA',
    -- Correção
    'COMPENSACAO'
));
