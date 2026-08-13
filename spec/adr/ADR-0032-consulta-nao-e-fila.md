# ADR-0032 — Consulta histórica não é fila

**Status:** aceito · **Data:** 2026-08 · **Relacionado:** INV-03/04/07/10/15, ADR-0018/0019

## Contexto

Entrada, Hoje e Executor são superfícies de ação. Usá-las para localizar uma decisão antiga exige
varrer estados ou conhecer onde o item terminou. Uma busca completa pode resolver isso, mas também
pode virar outra fila, um dashboard contemplativo ou um jardim de filtros salvos.

## Decisão

`/itens` é uma superfície exclusivamente de **consulta e navegação** sobre Pendências abertas e
fechadas. Ela não possui ordenação manual, edição de metadados, ações em lote, visões salvas,
etiquetas ou contadores de desempenho individual.

Cada resultado apresenta estado verificável e links para a superfície situada que possui a ação:
Entrada, bloco, delegação do executor, regra, instância ou trilha. Assim, a consulta não cria fila;
ela conduz ao contexto existente.

Busca textual é determinística e escopada por organização. Consulta natural continua escolhendo
apenas templates fechados e devolve as mesmas referências navegáveis; modelo não gera SQL.

## Consequências

- (+) histórico e estado ficam localizáveis sem duplicar operação;
- (+) filtros fixos são previsíveis e auditáveis;
- (+) fonte clicável é comum à busca textual e natural;
- (−) não haverá personalização de visões;
- (−) indexar conteúdo minimizado exige atualização incremental explícita.

