# 014 — Perguntas ao dossiê (índice híbrido BM25 + embeddings)

## Por quê
O bloco (013) traz o dossiê determinístico do item. O RFC-0004 §1 vai além: **responder perguntas
sobre a base do tenant**, com **fonte navegável** — "o Panorama já foi reprovado antes?", "qual foi o
último valor negociado?". A recuperação é o que faz o assistente pagar seu custo no momento aversivo,
sem virar chat que inventa (ADR-0019).

## O quê
- **Índice de passagens** por organização (`documento_indice`): trechos vindos da pendência, das
  mensagens brutas (evento_bruto) e da trilha, com **`tsv`** (full-text pt) e **`emb vector`** (pgvector).
- **Recuperação híbrida**: BM25 (`ts_rank_cd`) **+** similaridade de cosseno (`<=>`, quando há
  embedding), combinados; top-K acima de um **limiar**.
- **Guardrails (RFC-0004)**: **toda resposta cita fonte**; **sem passagem acima do limiar → "não
  encontrei isso na base"** (nunca completa de memória).
- **Síntese**: com o gateway (018), redige a resposta a partir das passagens (proposta); sem modelo
  (piloto/stub), devolve as próprias passagens como resposta — sempre com fonte.
- **API**: `POST /v1/pendencias/{id}/dossie/perguntar {pergunta}` (indexa sob demanda + recupera).
- **Web**: caixa "Perguntar ao dossiê" no bloco, com resposta + fontes clicáveis (→ trilha).

## Infra (pgvector em todos os ambientes)
- Extensão `vector` **pré-criada por admin** (dev: `postgres`; VPS: admin do `alcada-postgres`,
  já pgvector) + `CREATE EXTENSION IF NOT EXISTS vector` no Flyway (no-op p/ role não-super quando já
  existe; cria de fato no CI, onde o role é superusuário). `flyway clean` não remove a extensão (o
  role não é dono) — testes seguem verdes.
- CI passa a usar a imagem `pgvector/pgvector:pg16`.

## Degradação honesta no piloto
No `demo` não há modelo de embedding configurado → as passagens indexam **só** com `tsv` (emb nulo) e
a recuperação é **BM25**. O caminho vetorial (cosseno) está pronto e é **testado com vetores
injetados**; ativa quando houver endpoint de embedding real.

## Fora de escopo
- **Correção de premissa** da pergunta ("não foi maio, foi 08/07") e verificação factual do rascunho
  (RFC-0004) — incremento.
- Ingestão de **documentos anexos** e **ERP** — o índice cobre pendência + mensagens + trilha.
- Indexador em background/incremental — aqui a (re)indexação é sob demanda por pendência.

## Critério de aceite
- `perguntar` recupera passagens da base do tenant com **fonte**; abaixo do limiar responde "não
  encontrei isso na base".
- O caminho vetorial (cosseno) funciona quando há embedding (testado com vetor injetado); sem
  embedding, cai em BM25 sem quebrar.
- Nunca responde sem fonte; escopo por `org_id` (INV-15); nenhuma decisão por inferência (INV-10).
