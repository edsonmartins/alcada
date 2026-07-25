# ADR-0018 — A interface empurra; não oferece jardim para cuidar

**Status:** aceito · **Data:** 2026-07 · **Relacionado:** INV-01, INV-04

## Contexto
O gestor tem horas de desktop. Tempo disponível + interface agradável = risco de **cuidar** do
backlog em vez de esvaziá-lo: reorganizar, reclassificar, ajustar. É a armadilha do "vou me
organizar primeiro", com interface melhor.

## Decisão
Restrições permanentes de interface:

- **sem arrastar cartão** entre colunas ou listas
- **sem campo livre obrigatório** e sem taxonomia customizável pelo usuário final
- **sem visão puramente contemplativa**: toda tela oferece próxima ação
- **sessão improdutiva é nomeada**: se houve tempo de uso sem transição de estado, o sistema diz
- ação em lote é incentivada; edição item a item de metadado, não

## Consequências
- (+) Mantém o produto alinhado à métrica-alvo e diferenciado de gerenciadores de tarefa.
- (−) Contraria expectativa de usuários vindos de Trello/Pipefy; exige explicação no onboarding.
