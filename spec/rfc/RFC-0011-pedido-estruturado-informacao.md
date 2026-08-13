# RFC-0011 — Pedido estruturado de informação

**Status:** proposto · **Implementa:** ADR-0030 · **Pacote:** 031

## 1. Objetivo

Mover explicitamente a próxima ação para quem possui o insumo e trazer a resposta à Pendência
correta, sem criar tarefa ou caixa paralela.

## 2. Modelo

`pedido_informacao` contém `id`, `org_id`, `pendencia_id`, `contato_id`, `pergunta`, `prazo`,
`estado`, timestamps e ocorrência. Estados: `AGUARDANDO_ENVIO`, `AGUARDANDO_RESPOSTA`,
`RESPONDIDO`, `CANCELADO`, `VENCIDO`.

Restrição parcial garante no máximo um pedido aberto por Pendência. Pergunta é texto confirmado
pelo gestor; endereço do contato nunca entra na trilha.

## 3. Comando

```text
POST /v1/pendencias/{id}/pedidos-informacao
{ contatoId, pergunta, prazo }
```

O comando valida tenant, destinatário, pergunta e prazo; grava pedido, correlação, job, outbox e
transições na mesma transação. A interface reutiliza a busca de destinos externos do repasse e
oferece uma pergunta inicial baseada no título, sempre editável e confirmada pelo humano.

## 4. Comunicação e retorno

`PEDIDO_INFORMACAO` sai pelo Linktor com `metadata.alcada_correlation`. Após entrega, o pedido passa
a `AGUARDANDO_RESPOSTA`. O webhook valida token, tenant, canal e autor antes de registrar o retorno.

Em modo local habilitado para pedido estruturado, a regra determinística trava pedido e Pendência,
marca `RESPONDIDO`, coloca a Pendência em `ENTRADA` e registra trilha sem PII. A evidência minimizada
permanece em `retorno_delegacao` durante a transição de modelo; uma porta de leitura será
generalizada antes do dossiê.

## 5. Prazo e corrida

O job do prazo usa `(pedido_id, VENCER_PEDIDO_INFORMACAO)`. Se vencer primeiro, devolve a Pendência
à Entrada com pedido `VENCIDO`. Se a resposta travar primeiro, o job vira no-op. Não há timer em
memória e nenhuma das ordens produz duas transições.

## 6. Reversibilidade e idempotência

- outbox usa `(pedido_id, PEDIDO_INFORMACAO)`;
- desfazer antes da liberação cancela pedido e revoga correlação;
- `message.id` repetido não duplica retorno nem transição;
- um novo pedido só é permitido após o anterior estar terminal.

## 7. Fora desta fatia

- envio para pessoa interna sem canal resolvido;
- classificação por modelo ou resposta como comando;
- lembretes intermediários e recobrança automática;
- validação Linktor/WhatsApp/e-mail ao vivo.

