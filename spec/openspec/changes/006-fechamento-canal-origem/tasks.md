# Tasks — 006 fechamento no canal de origem

## Origem na pendência
- [x] Migration `V10`: colunas `origem_canal` / `origem_destino` / `origem_thread` em `pendencia`
- [x] `ProcessadorCaptura` grava a origem a partir do `evento_bruto` na criação da pendência

## Notificação (Despachante de produção)
- [x] Porta `Canal { enviar(org, EnviarMensagem): boolean }` + `CanalIndisponivel` + adaptador `LinktorStub`
- [x] `LinktorStub` configurável para falhar (`programarFalha`) e idempotente — contrato de sucesso E falha
- [x] `DespachanteCanal implements Despachante` — roteia por tipo; é o Despachante de produção
- [x] `item.fechado` → mensagem de fechamento ao solicitante (template neutro, sem interno)
- [x] `canal.resposta` → entrega o texto da resposta de captura (com `pendencia_id`)
- [x] Ignorar eventos internos (`delegacao.*`) — não vão ao solicitante
- [x] Ignorar pendência sem origem (escape manual) sem erro; marca entregue

## Trilha de comunicação
- [x] `COMUNICADA` no sucesso (destino por referência `{canal}`, sem identificador direto — ADR-0016)
- [x] `FALHA_COMUNICACAO` por `VarredorMortos` idempotente (worker do outbox segue agnóstico) — decisão 1b

## Testes
- [x] `item.fechado` entrega + `COMUNICADA`
- [x] só solicitante, sem deliberação interna (conteúdo da mensagem)
- [x] idempotência: reentrega não duplica
- [x] falha → esgota → `FALHA_COMUNICACAO` (varredor); varredor idempotente
- [x] eventos internos não avisam o solicitante (**fronteira ADR-0013 travada por teste**)
- [x] `canal.resposta` entregue
- [x] item sem origem não quebra (marcado entregue, sem retentativa presa)
- [x] `OutboxTransacionalTest` refatorado sobre o `LinktorStub` real (DespachanteFake removido)

## Decisões (fechadas)
1. `FALHA_COMUNICACAO` por varredor de mortos idempotente; `WorkerOutbox` agnóstico da trilha.
2. Endereço de retorno denormalizado na pendência.
3. Stub do Linktor com contrato explícito de sucesso E falha; ambos os caminhos de trilha testados.
4. Fronteira do ADR-0013 travada por teste (nenhum `delegacao.*` gera saída ao solicitante).

---
**Estado:** pacote 006 **completo** — o outbox agora entrega de verdade (fim do no-op). Backend
71 testes JVM, nativo ~70 MB RSS. Fecha o INV-09 que o 005 deixou como efeito.
