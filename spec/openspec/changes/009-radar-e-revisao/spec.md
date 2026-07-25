# Spec — 009 radar e revisão de sexta

## Cenário: radar mede quanto depende do gestor
**WHEN** o gestor chama `GET /v1/radar`
**THEN** `dependeDoGestor` conta como dependentes os itens em `ENTRADA`, `AGENDADA` e `DELEGADA` de
nível `N3`, sobre o total de itens abertos (`status <> FECHADA`)
**AND** retorna `qtd`, `total` e `pct`

## Cenário: radar mostra o que roda sem o gestor
**WHEN** existem delegações ativas de nível `N1` ou `N2`
**THEN** `rodandoSemVoce` conta essas delegações
**AND** delegações `N3` não entram nessa contagem (dependem do gestor)

## Cenário: radar lista adiados três vezes ou mais
**WHEN** há pendências com `adiado_count >= 3`
**THEN** `adiados` traz cada uma com título, contador de adiamento, o que trava e valor
**AND** a UI apresenta cada item com uma ação (resolver, soltar ou repassar) — nunca só o número (ADR-0017)

## Cenário: contagem honesta de autonomia
**WHEN** o radar agrega os desfechos de N2 na janela
**THEN** `executada deliberada`, `executada por ausência`, `devolvida pelo executor` e `escalada` são
números **distintos**
**AND** nenhum deles é somado a outro (ADR-0024)

## Cenário: contagem honesta de fechamento no canal
**WHEN** o radar agrega os fechamentos no canal de origem
**THEN** `entregue`, `falho` e `impossível` são três números distintos (ADR-0025)

## Cenário: série de encolhimento (INV-01)
**WHEN** o gestor abre o radar
**THEN** `encolhimento` traz 8 semanas com `entraram` (CAPTADA) e `fecharam` (resolvida/executada/
decidida no bloco) por semana, em fuso America/Sao_Paulo
**AND** a UI rotula como fluxo (entradas × fechamentos), com a leitura do INV-01

## Cenário: radar não é placar
**WHEN** qualquer superfície do radar expõe padrão pessoal
**THEN** ela traz ação e causa provável na mesma tela
**AND** não há score, ranking, streak nem comparação entre pessoas (ADR-0017)
**AND** não existe rota que exporte métrica individual do gestor

## Cenário: revisão de sexta é um roteiro sequencial
**WHEN** o gestor chama `GET /v1/revisao-semanal`
**THEN** retorna, em ordem: `entrada` (fila a esvaziar), `adiados` (3×+), `podeVirarRegra` (dica) e
`resumoSemana`
**AND** `resumoSemana` conta os eventos da trilha da semana corrente (resolvidas, executadas,
delegadas, escaladas, devolvidas, fechadas)

## Cenário: "pode virar regra" é dica, não regra
**WHEN** uma assinatura `{classe}` teve `>= 3` decisões `RESOLVIDA` nas últimas 4 semanas
**THEN** aparece em `podeVirarRegra` como **dica**, apontando para a mineração (RFC-0003)
**AND** o sistema **não** cria regra automaticamente aqui

## Cenário: isolamento por organização (INV-15)
**WHEN** dois tenants têm dados
**THEN** o radar e a revisão de um tenant nunca contam itens do outro
**AND** toda query carrega `org_id` no predicado (passa pelo GuardaOrgId)

## Cenário: leitura pura
**WHEN** o radar ou a revisão são consultados
**THEN** nenhuma transição de estado ocorre, nada é escrito na trilha e nenhum efeito externo é
enfileirado
