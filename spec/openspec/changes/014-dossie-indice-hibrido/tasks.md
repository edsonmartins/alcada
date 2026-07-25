# Tasks — 014 perguntas ao dossiê (índice híbrido)

## Infra pgvector
- [ ] Extensão pré-criada por admin em dev/test (postgres) e VPS (admin do alcada-postgres)
- [ ] db/init do alcada-devops cria a extensão em deploys novos
- [ ] CI: trocar imagem do Postgres para pgvector/pgvector:pg16

## Migration
- [ ] `V16__dossie_indice.sql`: `CREATE EXTENSION IF NOT EXISTS vector` + `documento_indice`
      (tsv gerado + emb vector(1024)) + índices gin/hnsw; GuardaOrgId conhece a tabela

## Backend — módulo assistente
- [ ] `assistente.port.Dossie` + DTOs (RespostaDossie{encontrou,resposta,fontes[]}, Fonte)
- [ ] `DossieJdbc.indexar`: passagens (pendência + evento_bruto + trilha); emb best-effort via gateway
- [ ] `DossieJdbc.perguntar`: híbrido (BM25 + cosseno quando há emb); limiar; "não encontrei"; síntese
      via gateway ou passagens; idempotência da indexação

## Backend — API
- [ ] `POST /v1/pendencias/{id}/dossie/perguntar` {pergunta}
- [ ] `@RegisterForReflection`; problem+json; docs/API.md

## Web — bloco
- [ ] Caixa "Perguntar ao dossiê" + resposta com fontes clicáveis (→ trilha) / "não encontrei"

## Testes
- [ ] Backend: BM25 com fonte; abaixo do limiar não inventa; caminho vetorial com emb injetado;
      sem emb cai em BM25; isolamento; indexação idempotente
- [ ] Web (Vitest): caixa de pergunta; resposta com fontes; "não encontrei"

## Verificação
- [ ] JVM suite verde + build nativo + RSS ≤120 MB
- [ ] Deploy no piloto (GHCR pull) e conferência da pergunta ao dossiê (BM25)
