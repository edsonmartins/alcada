-- 024 C6 — bot visível é pré-condição da captura (ADR-0011 §2). Ao ativar um grupo, a
-- Alçada publica um aviso no próprio grupo (via Linktor) tornando o assistente visível;
-- só depois de o aviso ser confirmado (aviso_em preenchido) o conteúdo do grupo passa a
-- ser capturado. Grupo ativo mas sem aviso publicado ainda → nada é ingerido.
ALTER TABLE grupo_acompanhado ADD COLUMN aviso_em timestamptz;
