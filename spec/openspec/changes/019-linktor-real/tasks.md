# Tasks — 019 linktor-real

Integração real do Linktor (saída + entrada), trocando o `LinktorStub`. Desenho aprovado via
plano + **ADR-0025** (fechamento condicionado à conversa inbound; `COMUNICACAO_IMPOSSIVEL`).

## Emenda e migration
- [x] ADR-0025 (emenda ADR-0021 + ADR-0016) — `COMUNICACAO_IMPOSSIVEL` ≠ `FALHA_COMUNICACAO`
- [x] Migration `V12`: `CHECK` da trilha com `COMUNICACAO_IMPOSSIVEL` (31 tipos); `fonte.linktor_channel_id`
- [x] `TipoEvento` += `COMUNICACAO_IMPOSSIVEL`

## Outbound (saída real)
- [x] `LinktorHttp implements Canal` — `java.net.http`, `POST /api/v1/conversations/{conversationId}/messages`,
      `X-API-Key`, corpo `{text, metadata:{source, idempotency_key}}` (padrão vendax.ai; sem SDK, native-safe)
- [x] Endereça por `conversationId` (= `EnviarMensagem.responderA` = `origem_thread`); ADR-0025
- [x] Falha/5xx → `CanalIndisponivel` → outbox reprocessa → varredor → `FALHA_COMUNICACAO`
- [x] **Só em `prod`** (`@IfBuildProfile`); stub `@DefaultBean` fora de prod — dev/test nunca batem no host externo
- [x] `DespachanteCanal`: sem conversa → `COMUNICACAO_IMPOSSIVEL` (não no-op, não FALHA)

## Inbound (webhook real)
- [x] `POST /v1/captura/linktor` — body cru, resolve fonte por `linktor_channel_id` (→ org + segredo)
- [x] HMAC-SHA256 `timestamp + "." + body`, tolerância 300s, comparação em tempo constante, fail-closed
- [x] Segredo é credencial: nunca em log/trilha/erro (só "assinatura inválida para fonte X")
- [x] Mapeia `message.received` → `MensagemRecebida` (canal, `threadRef=conversationId`, `autorExt=phone`, `mensagemId=message.id`)
- [x] Idempotente por `message.id` (reentrega → no-op limpo, 200, não 500)
- [x] `docs/API.md` atualizado (CLAUDE.md §8)

## Testes
- [x] Outbound (HttpServer in-process): requisição correta (URL/`X-API-Key`/idempotência); 5xx → `CanalIndisponivel`; sem key → indisponível
- [x] Inbound: assinatura válida ingere; inválida → 401; **timestamp fora de 300s → 401 (replay)**; reentrega → no-op 200
- [x] `COMUNICACAO_IMPOSSIVEL` (sem conversa) e `FALHA_COMUNICACAO` (havia canal, falhou) separados

## Notas
- Endereçamento outbound **só por conversation** (fato do contrato do Linktor): itens de origem
  sistema/webhook/escape nunca fecham o laço no canal — `COMUNICACAO_IMPOSSIVEL` é **desfecho
  terminal legítimo** para eles, não dívida. Portal (007) é mitigação **parcial** (só onde há
  endereço da contraparte), não a resolução. Ver ADR-0025.
- Para testar contra `api.linktor.dev`, criar um profile explícito `dev-integracao` (nunca o default).

---
**Estado:** implementado — 87 testes JVM (7 novos), nativo ~71 MB RSS, `Canal` real só em prod.
