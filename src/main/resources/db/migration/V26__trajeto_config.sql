-- ============================================================================
-- V26 — Config por tenant do modo trajeto (023)
-- Quais classes/limiar de valor são "de peso" e portanto recusadas em movimento
-- (viram bloco). Default preserva o comportamento atual (BLOQUEIO + >= 50k).
-- Config, não cadastro do gestor (INV-02): é ajuste operacional por org.
-- ============================================================================
ALTER TABLE organizacao
    ADD COLUMN trajeto_classes_recusaveis text[]  NOT NULL DEFAULT '{BLOQUEIO}',
    ADD COLUMN trajeto_valor_limite       numeric NOT NULL DEFAULT 50000;
