# Design — 006 fechamento no canal de origem

Referências: `adr/ADR-0013-multi-ator-e-portal-externo.md`, `adr/ADR-0021-linktor-camada-de-canais.md`.

## Módulos
`notificacao` (Despachante de produção + porta de canal). Toca `captura` (guardar origem na pendência)
e reusa `plataforma.outbox`/`plataforma.trilha`.

## Origem na pendência
A captura passa a persistir a origem, para fechar o laço em qualquer transição:
```sql
-- pendencia ganha:
origem_canal   text,   -- WHATSAPP | EMAIL | WEBHOOK
origem_destino text,   -- autor/endereço do solicitante no canal
origem_thread  text    -- referência de thread para responder encadeado
```
Preenchidas no `ProcessadorCaptura` a partir do `evento_bruto` (autor, canal, thread) — antes que o
bruto expire.

## Despachante (o que faltava)
`notificacao.internal.DespachanteCanal implements plataforma.outbox.port.Despachante`, `@DefaultBean`
(os testes ainda podem sobrepor). O worker do outbox passa a entregar de verdade.

Roteamento por tipo de evento do outbox:
| Evento | Ação |
|---|---|
| `item.fechado` | mensagem de fechamento ao solicitante no canal de origem |
| `canal.resposta` | entrega o texto da resposta de captura (id + estado) |
| `delegacao.executada` / `.escalada` / `.devolvida` | **ignorados aqui** — são internos (executor/gestor), não do solicitante |

Para `item.fechado`: lê a origem da pendência; monta `EnviarMensagem { canal, destino, texto,
responder_a, idempotency_key }` (ADR-0021); envia pela porta `Canal`.

## Porta de canal (contrato explícito de sucesso E falha)
```java
interface Canal {
  /** @return true se entregou agora; false se já entregue (idempotente por idempotency_key).
   *  @throws CanalIndisponivel quando a entrega falha (o outbox reprocessa). */
  boolean enviar(OrgId org, EnviarMensagem msg);
}
```
Adaptador `LinktorStub` (registra a intenção; não fala com WhatsApp/e-mail — isso é do Linktor).
**Configurável para falhar sob demanda** (`programarFalha(destino)`): sem isso o `FALHA_COMUNICACAO`
seria escrito sem nunca ter sido testado que dispara. Idempotência por `idempotency_key`: reentrega do
mesmo efeito não duplica, e o Despachante só grava `COMUNICADA` quando o envio foi **novo**.

## Trilha de comunicação
- Sucesso → `COMUNICADA` (ator `SISTEMA:motor:notificacao`), carga `{ canal }` — **sem** identificador
  direto (destino vai como referência, não como e-mail/telefone cru; ADR-0016).
- Esgotamento de retentativas → `FALHA_COMUNICACAO`, por um **varredor de mortos idempotente**
  (decisão 1b): o `WorkerOutbox` permanece **agnóstico** (não conhece a trilha); um `VarredorMortos`
  lê `outbox` com `status='ERRO' AND NOT falha_registrada`, grava `FALHA_COMUNICACAO` e marca
  `falha_registrada=true` — reexecutar não duplica.

## Fronteira travada por teste (ADR-0013)
Não basta a intenção: um teste verifica que **nenhum** evento `delegacao.*` gera saída ao solicitante
(zero chamadas ao `Canal`, zero `COMUNICADA`). É fácil o outbox crescer e alguém assinar um evento
interno para notificação externa sem perceber — o teste trava isso antes de virar vazamento.

## Fronteira (ADR-0013)
A mensagem ao solicitante carrega **estado e fechamento**, nunca deliberação interna, nome de decisor
ou histórico de reprovação de terceiros. O texto é gerado por template neutro; nenhum campo interno
atravessa.

## Fluxo
```
transição (FECHADA) ─► outbox item.fechado ─► WorkerOutbox ─► DespachanteCanal ─► Canal(Linktor stub)
                                                                     │ sucesso → trilha COMUNICADA
                                                                     └ falha → retry; esgota → FALHA_COMUNICACAO
```

## Riscos
Mapear pendência → origem exige que a captura tenha guardado a origem; itens criados manualmente
(escape, ADR-0005) podem não ter canal — nesse caso não há o que fechar no canal, e o Despachante
ignora com log (não é erro).
