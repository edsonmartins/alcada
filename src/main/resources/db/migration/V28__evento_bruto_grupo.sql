-- 024 F1 — captura ciente de grupo.
-- Marca o evento bruto que veio de uma conversa de GRUPO (Linktor: envelope.group).
-- Para grupo, thread_ref carrega o id do grupo (chat_jid); a bandeira distingue de
-- 1:1 sem precisar interpretar o formato do thread_ref. autor_ext segue sendo o
-- indivíduo que falou. INV-15: escopo por org_id já existente na tabela.
ALTER TABLE evento_bruto
    ADD COLUMN grupo boolean NOT NULL DEFAULT false;

-- Consultar os eventos de um grupo (o extrator por janela lê por thread do grupo).
CREATE INDEX IF NOT EXISTS ix_evento_bruto_grupo
    ON evento_bruto (org_id, thread_ref)
    WHERE grupo;
