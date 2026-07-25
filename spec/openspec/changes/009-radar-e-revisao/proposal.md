# 009 — Radar de gargalo e Revisão de sexta

## Por quê
O INV-01 diz que o produto **encolhe com o uso** — se o volume que depende do gestor não cai mês a
mês, falhou. Até aqui nada mostra essa curva. O **Radar** (`/radar`, WEB.md) é o diagnóstico que
torna o encolhimento visível; a **Revisão de sexta** (`/sexta`, roteiro de 20 min — MANUAL §rotina)
é o ritual que mantém a fila limpa e alimenta a migração de alçada.

Ambos são **superfícies de leitura** sobre dados que já temos (trilha, pendência, delegação,
adiamento). Não criam efeito externo, não chamam modelo, não escrevem nada.

## O quê
- **Módulo `metricas`** (hoje vazio): consultas de diagnóstico escopadas por organização (INV-15).
- **`GET /v1/radar`** — diagnóstico organizacional (ADR-0017): quanto trava no gestor, o que roda sem
  ele, adiados 3×+, pior espera, contadores honestos de autonomia e de fechamento, e a **série de
  encolhimento** (8 semanas).
- **`GET /v1/revisao-semanal`** — roteiro sequencial: fila de entrada, adiados 3×+, dica do que pode
  virar regra, e o resumo da semana.
- **Telas** `/radar` e `/sexta` (web), na linguagem do protótipo, com **métrica sempre acompanhada de
  ação e da causa provável** (ADR-0017) — sem placar, sem ranking, sem score.

## Contagem honesta (ADR-0024/0025)
O radar conta **separadamente**: executada deliberada × executada por ausência × devolvida pelo
executor × escalada; e no canal: entregue × falha × impossível. Somar produziria diagnóstico mentiroso.

## Fora de escopo
- **Mineração completa de regras N3→N2→N1** (RFC-0003) — pacote próprio; aqui só uma *dica* de
  repetição, marcada como dica, não regra.
- **Bloco de decisão / dossiê** (F4).
- **Snapshot histórico de estoque**: a série de encolhimento usa **fluxo** (entradas × fechamentos por
  semana) como proxy honesto, declarado como tal, até haver snapshot semanal.
- **Exportar métrica individual** do gestor — proibido (ADR-0017); só agregado organizacional.

## Critério de aceite
- `/radar` mostra o % que depende do gestor (ENTRADA + AGENDADA + DELEGADA N3 sobre abertos) e a
  série de 8 semanas; toda métrica de padrão pessoal vem com ação e causa nomeada.
- Radar conta ausência, deliberada, devolução e escalonamento como números distintos.
- `/sexta` percorre, em ordem: entrada pendente → adiados 3×+ → dica de regra → resumo da semana.
- Tudo escopado por `org_id`; nenhum número cruza tenant; nenhuma escrita nem efeito externo.
