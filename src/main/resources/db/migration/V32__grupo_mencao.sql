-- 024 C5 — menção direta fura o debounce. Quando chega, num grupo acompanhado, uma
-- mensagem que menciona alguém (o Linktor propaga `data.message.mentions`), marca-se
-- o instante da menção; o WorkerGrupos avalia a janela na hora, sem esperar a conversa
-- esfriar por `grupos.debounce-segundos`. (A Alçada não guarda o JID do gestor, então
-- qualquer menção num grupo monitorado fura o debounce — superconjunto seguro de
-- "gestor mencionado": só antecipa a avaliação, nunca deixa passar.)
ALTER TABLE grupo_acompanhado ADD COLUMN mencao_em timestamptz;
