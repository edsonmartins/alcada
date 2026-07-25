# ADR-0007 — Recobrança não cria item; aumenta temperatura

**Status:** aceito · **Data:** 2026-07

## Contexto
Item adiado gera recobrança. A recobrança chega por outro canal (quem mandou e-mail agora manda
mensagem) e parece item novo. O backlog percebido cresce mais rápido que o real, e o gestor sente
que "só aumenta".

## Decisão
Cobranças sobre a mesma pendência são deduplicadas e incrementam `temperatura`. A temperatura entra
na função de ordenação; a contagem da fila não muda.

Chave de deduplicação: entidade referenciada (integrador, pedido, contrato) + janela temporal +
similaridade semântica acima de limiar. Toda fusão é registrada na trilha e reversível ("não é o
mesmo item").

O canal de origem recebe resposta imediata com o estado atual — é isso que faz o time parar de
cobrar.

## Consequências
- (+) Fila única não vira bagunça única.
- (+) Temperatura é sinal de priorização mais honesto que urgência declarada.
- (−) Fusão errada esconde pendência real; exige reversão de 1 toque e log auditável.
- (−) Exige resolução de entidade razoável em texto informal de mensageria.
