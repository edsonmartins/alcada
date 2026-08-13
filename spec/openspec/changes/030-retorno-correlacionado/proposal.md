# 030 — Retorno correlacionado pelo canal

**Fase:** fechamento do ciclo · **Implementa:** ADR-0029 · RFC-0010
**Depende de:** 019, 025, 029 e mudança coordenada no Linktor

## Problema

O contato é avisado, mas sua resposta não retorna à delegação. O gestor precisa copiar, cobrar ou
reconciliar manualmente, quebrando a fila única.

## Proposta

- correlação explícita e opaca em metadata;
- retorno minimizado e append-only;
- classificação proposta, sem execução por texto livre;
- suspensão determinística de N2 quando o retorno exige análise;
- item acionável volta à Entrada com evidência no dossiê;
- idempotência, isolamento e tratamento de corrida com a virada.

## Não-objetivos

- correlacionar por telefone, conversa ou similaridade;
- concluir delegação a partir de linguagem natural;
- criar chat ou caixa de retornos;
- implementar pedido estruturado de informação (031);
- alterar o Linktor neste repositório.

## Gate de entrada

Fixture real do Linktor comprovando ida e volta de `alcada_correlation`. Sem isso, a fatia da Alçada
pode ser escrita/testada com fixture, mas não marcada como validada ao vivo.
