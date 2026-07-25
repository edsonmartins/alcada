-- ============================================================================
-- V16 — Índice do dossiê (pacote 014, RFC-0004 §1) — híbrido BM25 + pgvector
-- A extensão é pré-criada por admin em cada ambiente; aqui o IF NOT EXISTS é
-- no-op para role não-superusuário (dev/VPS) e cria de fato onde o role é
-- superusuário (CI). O flyway clean não remove a extensão (o role não é dono).
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documento_indice (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       uuid NOT NULL,
    pendencia_id uuid,
    fonte_tipo   text NOT NULL CHECK (fonte_tipo IN ('PENDENCIA', 'MENSAGEM', 'TRILHA')),
    fonte_ref    text,
    texto        text NOT NULL,
    tsv          tsvector GENERATED ALWAYS AS (to_tsvector('portuguese', coalesce(texto, ''))) STORED,
    emb          vector(1024)                       -- nulo até haver endpoint de embedding
);

CREATE INDEX ix_docidx_tsv ON documento_indice USING gin (tsv);
CREATE INDEX ix_docidx_emb ON documento_indice USING hnsw (emb vector_cosine_ops);
CREATE INDEX ix_docidx_org ON documento_indice (org_id, pendencia_id);
