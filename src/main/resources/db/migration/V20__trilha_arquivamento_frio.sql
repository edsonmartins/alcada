-- ============================================================================
-- V20 — Arquivamento frio da trilha (004)
-- A trilha é imutável (ADR-0016): nada é deletado. O expurgo é "frio" — destaca
-- (DETACH) as partições mensais além da janela de retenção. A tabela destacada
-- continua existindo (auditável se reanexada), só sai do caminho quente.
-- ============================================================================
CREATE OR REPLACE FUNCTION trilha_arquiva_frias(retencao_meses int)
RETURNS int
LANGUAGE plpgsql AS $$
DECLARE
    corte date := (date_trunc('month', now()) - (retencao_meses || ' month')::interval)::date;
    r      record;
    n      int := 0;
BEGIN
    FOR r IN
        SELECT c.relname AS nome
        FROM pg_inherits i
        JOIN pg_class c ON c.oid = i.inhrelid
        JOIN pg_class p ON p.oid = i.inhparent
        WHERE p.relname = 'trilha'
          AND c.relname ~ '^trilha_[0-9]{4}_[0-9]{2}$'
          AND to_date(substring(c.relname FROM 8), 'YYYY_MM') < corte
    LOOP
        EXECUTE format('ALTER TABLE trilha DETACH PARTITION %I', r.nome);
        n := n + 1;
    END LOOP;
    RETURN n;
END;
$$;
