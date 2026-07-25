# ADR-0001 — Pendência como unidade atômica

**Status:** aceito · **Data:** 2026-07 · **Contexto:** modelagem de domínio

## Contexto
Ferramentas de produtividade modelam "tarefa": algo a fazer, com esforço e prazo. Aplicado a gestor,
isso mistura trabalho próprio com decisões de terceiros e produz uma lista que só cresce.

## Decisão
A unidade atômica é a **pendência**: algo que espera uma decisão do gestor. Se ninguém está
esperando, não é pendência — é iniciativa, e pertence ao horizonte trimestral ou a lugar nenhum.

Atributos obrigatórios: `quem_espera`, `o_que_trava`, `origem`. Atributos derivados: `custo_atraso`,
`temperatura`, `horizonte`. Atributos **proibidos** como obrigatórios: esforço estimado, prioridade
declarada, categoria livre, projeto.

## Consequências
- (+) A fila mede bloqueio de terceiros, não volume de trabalho — habilita a métrica de desbloqueio.
- (+) Elimina a maior fonte de ruído: ideia sem dono ocupando espaço de decisão.
- (−) Exige classificar entrada que não tem quem espera explícito (ex.: promessa em reunião).
- (−) Itens legítimos sem solicitante (iniciativa própria) precisam de tratamento separado.

## Alternativas rejeitadas
- **Tarefa genérica com flag "aguardando":** mantém a lista como centro de gravidade; não muda a métrica.
- **Ticket:** carrega SLA e fila de atendimento, semântica errada para decisão gerencial.
