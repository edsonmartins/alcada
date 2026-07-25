# ADR-0003 — Três níveis de autonomia como motor do sistema

**Status:** aceito · **Data:** 2026-07 · **Relacionado:** INV-06, ADR-0004

## Contexto
Delegar sem definir autonomia produz o pior dos mundos: o executor não sabe se pode agir e devolve a
decisão. O backlog volta para o gestor com atraso adicional.

## Decisão
Toda delegação carrega nível obrigatório:

| Nível | Contrato | Conta como dependente do gestor? |
|---|---|---|
| N1 | faz e informa depois | não |
| N2 | propõe e executa se não houver intervenção no prazo | não |
| N3 | propõe e aguarda decisão | **sim** |

O objetivo declarado do sistema é migrar itens N3 → N2 → N1. A promoção é sugerida por mineração de
padrão (RFC-0003) e aplicada como regra de autonomia por classe, não por item.

## Consequências
- (+) Métrica de encolhimento fica mensurável e atribuível.
- (+) Delegação vira contrato explícito entre duas pessoas, com prazo.
- (−) Exige modelo de permissão por classe de decisão, com valor-limite e escopo.
- (−) N3 pode ser usado como escape permanente; o radar expõe a proporção.

## Alternativas rejeitadas
- **Delegation Poker completo (7 níveis):** granularidade que ninguém usa em operação diária.
- **Autonomia por pessoa, não por item:** perde o caso comum de "confio nela nisso, não naquilo".
