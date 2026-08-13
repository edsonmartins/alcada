# ADR-0034 — Resumo diário é envelope de despacho, não nova fila

**Status:** aceito · **Data:** 2026-08 · **Relacionado:** INV-01/03/04/06/07/14/15,
ADR-0017/0018/0021/0031

## Contexto

O resumo de exceções do P032 já oferece calendário, preferência, entrega por Linktor e uma chave
idempotente por gestor, período e data local. Seu conteúdo, porém, é apenas uma contagem textual de
delegações próximas do prazo, pedidos vencidos e falhas de comunicação. O gestor ainda precisa abrir
o produto e varrer superfícies para descobrir onde agir.

Criar uma segunda notificação diária ou enviar uma mensagem por Pendência quebraria a fila única,
consumiria o teto de manutenção e transformaria o plano de controle em vigilância de atividade.

## Decisão

O P035 **evolui o resumo de exceções existente** para um envelope de despacho. Por usuário e período
há no máximo um retrato imutável e uma tentativa lógica de entrega, com:

1. até três Pendências da função Hoje;
2. delegações N2 cuja execução por ausência ocorrerá antes do próximo período configurado;
3. retornos observados que ainda exigem decisão humana;
4. escalonamentos ainda não absorvidos por uma nova saída;
5. uma estimativa opcional de tempo para despachar o conjunto.

Cada exemplo leva à ação canônica da Pendência ou do retorno. O resumo mostra totais quando uma
categoria excede os exemplos, mas não vira uma lista navegável paralela. Resumo vazio é silêncio.
Uma mudança posterior no estado não reabre nem reenvia o retrato do mesmo período.

“Retorno que exige decisão” significa retorno `OBSERVADO`, ligado a alvo do usuário e a uma
Pendência não fechada. O humano pode considerá-lo (`APLICADO`) ou rejeitá-lo (`REJEITADO`); considerar
o retorno não executa efeito externo nem escolhe uma das quatro Saídas. Essa deliberação é registrada
na Trilha e o encaminhamento da Pendência continua explícito.

A estimativa não é pontuação. Ela usa a mediana dos intervalos entre decisões consecutivas do próprio
usuário em sessões observadas de despacho nos últimos 90 dias, ignorando intervalos superiores a
15 minutos. Só aparece com amostra mínima de cinco intervalos, é arredondada para blocos de cinco
minutos e acompanha ação direta. Não é exportada, comparada entre pessoas nem usada para cobrança.

O módulo `notificacao` compõe o envelope por portas publicadas pelos módulos donos dos dados. Ele não
consulta tabelas de triagem, autonomia ou métricas diretamente. A entrega continua exclusivamente
pela Linktor e pela preferência já existente.

## Consequências

- (+) o usuário sabe onde intervir sem varrer filas;
- (+) preserva a deduplicação e a preferência implantadas no P032;
- (+) retornos deixam de reaparecer indefinidamente sem deliberação;
- (+) a estimativa é derivada de comportamento observado e oferece ação, sem ranking;
- (-) o retrato pode ficar desatualizado após ser gerado, deliberadamente, para impedir reenvio;
- (-) usuários sem histórico suficiente não recebem estimativa inicialmente;
- (-) a composição exige portas entre módulos e um retrato persistido do conteúdo.
