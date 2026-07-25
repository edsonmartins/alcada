# Design — 014 índice híbrido do dossiê

## Migration V16
```
CREATE EXTENSION IF NOT EXISTS vector;   -- no-op onde já existe (pré-criada por admin); cria no CI
CREATE TABLE documento_indice (
  id uuid pk, org_id uuid not null, pendencia_id uuid,
  fonte_tipo text not null,          -- PENDENCIA | MENSAGEM | TRILHA
  fonte_ref text,                    -- id/âncora navegável
  texto text not null,
  tsv tsvector GENERATED ALWAYS AS (to_tsvector('portuguese', coalesce(texto,''))) STORED,
  emb vector(1024)                   -- nulo até haver endpoint de embedding
);
CREATE INDEX ix_docidx_tsv ON documento_indice USING gin (tsv);
CREATE INDEX ix_docidx_emb ON documento_indice USING hnsw (emb vector_cosine_ops);
CREATE INDEX ix_docidx_org ON documento_indice (org_id, pendencia_id);
```
`documento_indice` entra no GuardaOrgId (DADOS_TENANT). `tsv` é coluna gerada (auto-mantida).

## Indexação (assistente)
`indexar(org, pendenciaId)` — idempotente: apaga as passagens da pendência e reinsere:
- PENDENCIA: `titulo` + `o_que_trava` + `quem_espera` (fonte_ref = pendenciaId)
- MENSAGEM: `texto` de cada `evento_bruto` ligado (via cobranca/origem) — best-effort
- TRILHA: `carga`/estados relevantes (fonte_ref = evento da trilha)
Para cada passagem, `emb` = `gateway.embutir(texto)` **best-effort** (try/catch → nulo). Sem modelo,
fica só `tsv`.

## Recuperação híbrida
`perguntar(org, pendenciaId, pergunta)`:
1. `indexar` a pendência (garante frescor) — barato para um item.
2. BM25: `ts_rank_cd(tsv, plainto_tsquery('portuguese', :q))` sobre `org_id` (opcionalmente toda a
   base do tenant, não só a pendência — perguntas cruzam itens).
3. vetorial (se houver `emb` da query): `1 - (emb <=> :qemb)` como similaridade.
4. score = `w_bm25*bm25_norm + w_vec*cos` (pesos config; sem emb → só BM25). top-K acima do `limiar`.
5. sem resultado acima do limiar → `RespostaDossie(encontrou=false, "não encontrei isso na base", [])`.
6. com resultados → `fontes[]` (texto + fonte_tipo + fonte_ref); `resposta` = síntese via
   `gateway.redigir` (proposta) ou, sem modelo, as próprias passagens concatenadas.

## API
```
POST /v1/pendencias/{id}/dossie/perguntar   { pergunta }
   -> { encontrou, resposta, fontes:[{fonteTipo, fonteRef, trecho}] }
```

## Testabilidade do caminho vetorial
Sem modelo real, um teste insere passagens com `emb` **injetado** (vetor 1024) e uma query com `emb`
conhecido, verificando que o cosseno recupera a passagem correta. Assim o pgvector é exercido de fato.

## Multi-tenant / reflexão / native
Predicados com `org_id`. DTOs via `Response` → `@RegisterForReflection`. `vector` é serializado como
texto `[...]` nas queries nativas (set/parse manual); nenhuma dependência de SDK (native-friendly).

## Fora do design
- Correção de premissa; verificação factual; anexos/ERP; indexador incremental em background.
- Escolha do modelo de embedding e dimensão real (fixado `vector(1024)`; ajuste é migration futura).
