# Tasks — 001 captura multicanal

## Infra
- [x] Migrations das tabelas de captura + índices (org_id, status, entidade) — V5/V6, todas com org_id
- [x] Job de expurgo de `evento_bruto` por `expira_em` (JDBC direto, cross-tenant)
- [x] Fila de ingestão via scheduler persistente (job `PROCESSAR_CAPTURA`) — nada processado no thread do webhook

## Ingestão
- [x] Contrato `MensagemRecebida` / `EnviarMensagem` com o Linktor (ADR-0021)
- [x] Consumidor de entrada com dedup por `mensagem_id` (idempotência por `UNIQUE (fonte_id, mensagem_id)`)
- [ ] **Linktor:** threading de e-mail e isolamento do trecho novo — responsabilidade do Linktor (recebemos o trecho isolado)
- [ ] **Linktor:** captura seletiva por fonte + log auditável de proporção — configuração no Linktor
- [ ] **Linktor:** expurgo do bruto por `expira_em` — o bruto de canal vive no Linktor; aqui expurgamos a referência
- [x] Adaptador de webhook próprio autenticado por fonte (`POST /v1/captura/eventos`, id+segredo)
- [x] Cadastro de fonte declarada (`POST/GET /v1/fontes`, desativar) + resposta no canal (ADR-0011)

## Processamento
- [x] Normalizador para formato canônico (envelope do Linktor + trecho isolado)
- [x] Filtro de relevância determinístico + log auditável de descarte (`descarte_captura`)
- [x] Minimizador/pseudonimizador + re-hidratação local (ADR-0020 §3)
- [x] Extrator via `ModelGateway` com schema estrito e validação (1 reprocesso → baixa confiança)
- [x] Resolvedor de entidade (índice de apelidos evolui com confirmações — base criada)
- [x] Deduplicador com temperatura + reversão de fusão (`desfundir`)
- [x] Classificador + roteador por regra de autonomia vigente (fatia mínima de `regra_autonomia`)

## Superfície
- [x] `POST /v1/captura/eventos`
- [x] Resposta automática no canal de origem com estado do item (via outbox)
- [ ] Descarte de 1 toque na triagem, realimentando o filtro — a superfície de triagem é o pacote 003

## Observabilidade
- [~] Descarte auditável + `chamada_modelo` (custo/tokens) já persistidos
- [ ] Métricas de recall (amostragem auditada), precisão de classe, latência p95 — pacote 009 (radar-e-metricas)
- [ ] Painel Grafana da captura — pacote 009

## Testes
- [~] Corpus anonimizado — teste de vazamento do minimizador com corpus sintético (o corpus real do piloto entra em F1 operação)
- [x] Teste de idempotência de reentrega de webhook
- [x] Teste de expurgo e de não-varredura (7 cenários do spec + expurgo)

## Placeholders assumidos (aprovados no plano)
- Dedup por similaridade textual (Jaccard 0,82) até `pgvector`/embeddings.
- Linktor ausente: ingestão pelo webhook; saída por porta com adaptador stub via outbox.
- Fatia mínima de `regra_autonomia` (gestão em 008; máquina de delegação em 002).
