# ADR-0025 — Fechamento no canal condicionado à conversa inbound

**Status:** aceito · **Data:** 2026-07 · **Emenda:** ADR-0021 e ADR-0016 (anexo) · **Nota a:** INV-09 · **Relacionado:** pacote 006, integração real do Linktor

## Contexto
A integração real com o Linktor (guia oficial + padrão do vendax.ai) revelou uma restrição de
contrato que não estava no corpus: **o envio de mensagem é endereçado por `conversation_id`, e só
existe conversa para responder se ela chegou antes por inbound (webhook).** Não há envio por telefone,
nem criação de conversa outbound-first.

Consequência de **produto**, não só de plumbing: o fechamento no canal de origem (INV-09 — "quem pediu
recebe estado e fechamento no canal") é **impossível** para itens que não nasceram de uma conversa
inbound: origem **webhook/sistema**, **escape manual** (ADR-0005), ou qualquer pendência sem
`conversationId`. Fingir que avisou seria pior do que não avisar.

## Decisão

### 1. O fechamento no canal fica condicionado à conversa inbound
A resposta ao solicitante (006) exige um `conversationId` (guardado em `pendencia.origem_thread` a
partir do inbound). Sem ele, **não há canal para fechar** — e isso é registrado, não silenciado.

### 2. Duas trilhas semanticamente separadas — desde a migration
| Evento | Significado | Quando |
|---|---|---|
| `COMUNICADA` | avisou com sucesso | entregou no canal |
| `FALHA_COMUNICACAO` | **tentou e falhou** | havia canal; entrega esgotou retentativas |
| `COMUNICACAO_IMPOSSIVEL` | **não havia canal** | item sem conversa inbound (webhook/sistema/escape) |

`COMUNICACAO_IMPOSSIVEL` **não** é `FALHA_COMUNICACAO`. "Não havia canal" e "tentou e falhou" são
fatos diferentes, e o radar (009) precisa contá-los separadamente — somá-los produziria diagnóstico
errado (fechamentos que não chegaram a ninguém escondidos como falhas de entrega, ou vice-versa). A
separação nasce na `CHECK` da trilha, não é refino posterior.

### 3. Emenda ao anexo normativo do ADR-0016
Acrescenta ao grupo **Comunicação**:
```
COMUNICACAO_IMPOSSIVEL   — fechamento sem canal de conversa (ator SISTEMA:motor:notificacao)
```
Carga: `{ motivo: "sem_conversa" }`. Vocabulário fechado passa de 30 para 31 tipos.

## Nota ao INV-09
O INV-09 continua valendo **onde há canal**. Onde não há, o sistema **registra a impossibilidade**
(`COMUNICACAO_IMPOSSIVEL`) — não simula um aviso que não saiu. O laço com o solicitante depende de
existir um canal de conversa; itens de sistema/escape não têm contraparte de canal por definição.

## `COMUNICACAO_IMPOSSIVEL` é estado terminal legítimo — não é dívida
Para a classe de itens de **origem sistema/webhook/escape manual**, não existe canal porque **não
houve conversa** — e nunca haverá, para aquele item. `COMUNICACAO_IMPOSSIVEL` é, portanto, um
**desfecho terminal legítimo**, não um problema aguardando solução. É fato do mundo (o item não
nasceu de uma conversa com um solicitante), não limitação a corrigir. O sistema o registra como
verdade e segue; ninguém "resolve" a ausência de um canal que nunca existiu.

O **portal (007) é mitigação PARCIAL, não a resolução**: cobre apenas o subconjunto em que **há
endereço conhecido da contraparte** e faz sentido oferecer um link. Fora desse subconjunto,
`COMUNICACAO_IMPOSSIVEL` permanece o desfecho correto e final — e o radar (009) deve tratá-lo como
tal, não como falha a reduzir.

## Consequências
- (+) Radar honesto: fechamento entregue, falho e impossível são três números distintos — e o
  "impossível" é contado como desfecho legítimo, não como meta de redução.
- (+) A restrição real do Linktor fica no corpus como **fato**, não é descoberta em produção nem
  confundida com dívida técnica.
