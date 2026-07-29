-- 024 F2 — marca de avaliação por janela do grupo (debounce + poll).
-- O WorkerGrupos varre grupos ativos cuja conversa "assentou" (ultimo_visto mais
-- velho que o debounce) e que têm conteúdo novo desde a última avaliação
-- (ultimo_visto > avaliado_em). Nada de timer em memória (CLAUDE.md §4): o estado
-- do debounce mora na tabela, o tick só reserva com FOR UPDATE SKIP LOCKED.
ALTER TABLE grupo_acompanhado ADD COLUMN avaliado_em timestamptz;

-- Poll barato: só grupos ativos, ordenados por quando foram vistos por último.
CREATE INDEX ix_grupo_acomp_poll ON grupo_acompanhado (ultimo_visto) WHERE ativa;
