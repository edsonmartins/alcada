# Tasks — 014 perguntas ao dossiê (índice híbrido)

## Infra pgvector
- [x] Extensão pré-criada por admin em dev/test (role postgres)
- [x] db/init do alcada-devops cria a extensão em deploys novos (admin)
- [x] CI: imagem do Postgres → pgvector/pgvector:pg16
- [ ] VPS: criar a extensão no banco existente (one-off admin) antes do deploy

## Migration
- [x] `V16__dossie_indice.sql`: `CREATE EXTENSION IF NOT EXISTS vector` + `documento_indice`
      (tsv gerado + emb vector(1024)) + índices gin/hnsw; GuardaOrgId conhece a tabela

## Backend — módulo assistente
- [x] `assistente.port.Dossie` + DTOs (RespostaDossie{encontrou,resposta,fontes[]}, Fonte)
- [x] `DossieJdbc.indexar`: passagens (pendência + evento_bruto/mensagens); emb best-effort via gateway
- [x] `DossieJdbc.perguntar`: híbrido (BM25 + cosseno quando há emb); "não encontrei"; síntese via
      gateway ou passagens; indexação idempotente

## Backend — API
- [x] `POST /v1/pendencias/{id}/dossie/perguntar` {pergunta} (no BlocoResource)
- [x] `@RegisterForReflection`; problem+json; docs/API.md

## Web — bloco
- [x] Caixa "Perguntar ao dossiê" + resposta com fontes / "não encontrei"

## Testes
- [x] Backend: BM25 com fonte; abaixo do limiar não inventa; caminho vetorial com emb injetado
      (cosseno pgvector); isolamento; indexação idempotente — DossieTest (5)
- [x] Web (Vitest): caixa de pergunta; resposta com fontes — bloco.test.tsx

## Verificação
- [x] JVM suite verde (129) + 32 Vitest
- [ ] build nativo + RSS ≤120 MB
- [ ] Deploy no piloto (GHCR pull) e conferência da pergunta ao dossiê (BM25)
