-- ============================================================================
-- V36 — Compromisso no calendário do gestor (RFC-0009, fatia F2.3)
-- O lembrete pode virar evento na agenda. Guardamos o id do evento no provedor
-- para poder cancelá-lo e para não criar duas vezes.
-- ============================================================================
ALTER TABLE pendencia ADD COLUMN evento_calendario_id text;

COMMENT ON COLUMN pendencia.evento_calendario_id IS
    'id do evento no provedor (Google/Outlook) — só em pendências origem=LEMBRETE';
