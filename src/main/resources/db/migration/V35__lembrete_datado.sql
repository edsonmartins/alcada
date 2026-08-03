-- ============================================================================
-- V35 — Lembrete datado (RFC-0009, fatia F2.1)
-- O RESOLVER passa a poder deixar um compromisso com data. O lembrete NÃO é uma
-- segunda caixa: nasce como pendência em DORMINDO e volta pela Entrada no dia
-- (fila única, INV-03), reusando o DESPERTAR que já existe.
-- ============================================================================

-- De onde veio o item. 'LEMBRETE' não é captura: não conta como "entrou" no
-- encolhimento (INV-01), que soma eventos CAPTADA — o lembrete não emite CAPTADA.
ALTER TABLE pendencia ADD COLUMN origem text NOT NULL DEFAULT 'CAPTURA'
    CHECK (origem IN ('CAPTURA', 'ESCAPE', 'LEMBRETE'));

-- O item que foi resolvido e deixou este lembrete (null nos demais).
ALTER TABLE pendencia ADD COLUMN origem_pendencia_id uuid REFERENCES pendencia(id);

CREATE INDEX ix_pendencia_lembrete ON pendencia (org_id, origem_pendencia_id)
    WHERE origem = 'LEMBRETE';

-- Vocabulário da trilha — anexo normativo do ADR-0016, acrescentado pela RFC-0009.
-- COMPROMISSO_AGENDADO/FALHA_COMPROMISSO são da entrega no calendário (F2.3);
-- entram aqui para não exigir um segundo ALTER da constraint.
ALTER TABLE trilha DROP CONSTRAINT trilha_tipo_valido;

ALTER TABLE trilha ADD CONSTRAINT trilha_tipo_valido CHECK (tipo IN (
    -- Captura
    'CAPTADA', 'FUNDIDA', 'DESFUNDIDA', 'ROTEADA_POR_REGRA',
    -- Triagem
    'RESOLVIDA', 'REPASSADA', 'RESERVADA', 'REPOUSADA', 'ADIADA', 'DESPERTADA', 'DESCARTADA',
    -- Lembrete e compromisso (RFC-0009)
    'LEMBRETE_CRIADO', 'COMPROMISSO_AGENDADO', 'FALHA_COMPROMISSO',
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
