# Spec — 034 revisão resolutiva

## C1 — sessão é idempotente
**WHEN** o gestor inicia duas vezes uma revisão aberta
**THEN** recebe a mesma sessão, isolada por organização e gestor.

## C2 — Entrada é triada dentro do roteiro
**WHEN** há Pendência em Entrada
**THEN** o passo oferece as quatro Saídas e, após uma transição válida, avança sem edição de metadados.

## C3 — adiado recorrente ganha saída
**WHEN** uma Pendência tem três ou mais Adiamentos
**THEN** oferece Resolver, Repassar, Repousar ou abrir Bloco de decisão, com fonte navegável.

## C4 — regra exige evidência e decisão humana
**WHEN** existe proposta minerada
**THEN** mostra evidências e aceita exatamente Aceitar, Recusar ou Observar
**AND** nenhuma regra é ativada por inferência.

## C5 — promoção é conservadora
**WHEN** Delegação N3/N2 satisfaz o limiar sem devolução ou escalonamento
**THEN** sugere apenas o próximo nível N2/N1 e exige confirmação humana.

## C6 — falha impede promoção indevida
**WHEN** há devolução, escalonamento, evidência insuficiente ou limite de Classe
**THEN** a candidata não aparece ou a confirmação é rejeitada sem efeito parcial.

## C7 — trimestre tem ação explícita
**WHEN** fluxo operacional invade o Horizonte TRIMESTRE
**THEN** mostra causa, impacto e ação para proteger agenda
**AND** nada é agendado sem confirmação.

## C8 — fechamento mede redução
**WHEN** a sessão é concluída
**THEN** resume transições separadas, Pendências distintas removidas da dependência e remanescentes.

## C9 — sessão improdutiva é nomeada
**WHEN** nenhuma transição ocorreu
**THEN** o fechamento informa isso diretamente e oferece a primeira próxima ação, sem score ou culpa.

## C10 — corrida não duplica resultado
**WHEN** ação e conclusão concorrem ou concluir é repetido
**THEN** a sessão fecha uma vez e devolve o mesmo resumo.

## C11 — isolamento por organização
**WHEN** ids, evidências ou sessão pertencem a outro tenant
**THEN** responde vazio/404 sem revelar existência e sem escrever Trilha.

## C12 — revisão não vira jardim
**WHEN** o gestor percorre `/sexta`
**THEN** não encontra drag-and-drop, prioridade, etiqueta, ordenação ou edição em massa.

