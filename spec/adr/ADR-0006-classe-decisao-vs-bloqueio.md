# ADR-0006 — Classe do item define o roteamento padrão

**Status:** aceito · **Data:** 2026-07 · **Relacionado:** ADR-0012

## Contexto
Tratar tudo como decisão do gestor enche a mesa dele de coisa operacional. Uma rota de integração
parada não é decisão gerencial: é bloqueio que alguém resolve.

## Decisão
Toda pendência é classificada antes da triagem:

| Classe | Roteamento padrão | Gestor entra quando |
|---|---|---|
| `DECISAO` | triagem do gestor | sempre |
| `BLOQUEIO` | execução N1 pelo dono técnico | exige decisão comercial, gasto, ou estourou SLA |
| `ESTEIRA` | checklist da etapa | checklist falha ou há exceção de julgamento |

A classificação é sugerida pelo modelo e confirmada por regra determinística (INV-10).

## Consequências
- (+) Remove da mesa do gestor uma classe inteira de ruído operacional.
- (+) Permite prometer, na venda, "isso nunca chega em você" com mecanismo por trás.
- (−) Classificação errada de BLOQUEIO como DECISAO é invisível (só enche a fila); o inverso é
  perigoso (decisão executada por técnico). Default conservador: na dúvida, DECISAO.

## Alternativas rejeitadas
- **Uma fila indiferenciada com prioridade:** foi o desenho inicial; não sobrevive a integração com
  monitoramento, que gera volume alto de itens não-gerenciais.
