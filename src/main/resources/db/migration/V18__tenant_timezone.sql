-- Fuso horário por organização (002). Default preserva o comportamento atual (SP).
-- Só afeta apresentação/ancoragem de dia (radar, revisão, bloco, aprendizado);
-- prazos e janelas continuam em instantes absolutos (timestamptz).
ALTER TABLE organizacao ADD COLUMN timezone text NOT NULL DEFAULT 'America/Sao_Paulo';
