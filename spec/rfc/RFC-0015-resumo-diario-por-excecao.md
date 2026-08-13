# RFC-0015 — Resumo diário por exceção

**Status:** implementado · **Implementa:** ADR-0034 · **Pacote:** 035

## Envelope persistido

`resumo_diario` identifica `(org_id, gestor_id, periodo, data_local)` de forma única e guarda o
retrato JSON minimizado, totais, estimativa opcional, estado de entrega e timestamps. O job do P032
passa a gerar esse registro antes de publicar `RESUMO_EXCECOES`; a chave da outbox continua
`gestor:resumo:periodo:data`. Reprocessamento relê o mesmo retrato e não gera uma segunda versão.

O retrato contém no máximo três exemplos por categoria e o total real:

```text
hoje[]                 { pendenciaId, titulo, justificativa, acaoUrl }
n2PrestesExecutar[]    { pendenciaId, delegacaoId, titulo, executarEm, acaoUrl }
retornosDecisao[]      { retornoId, pendenciaId, titulo, recebidoEm, trecho, acaoUrl }
escalonamentos[]       { pendenciaId, delegacaoId, titulo, ocorridoEm, acaoUrl }
estimativaMinutos      inteiro|null
```

O `trecho` já é o conteúdo minimizado persistido pelo P030. Nenhum endereço, token de correlação ou
conteúdo bruto integra o envelope.

## Critérios determinísticos

- **Hoje:** exatamente a ordenação canônica da porta de triagem, limitada a três;
- **N2 prestes a executar:** delegação `AGUARDANDO_JANELA` com job `VIRADA` agendado até o próximo
  resumo ativo do usuário; na ausência de próximo período, usa o fim do próximo dia comercial;
- **retorno:** estado `OBSERVADO`, alvo pertencente ao gestor e Pendência diferente de `FECHADA`;
- **escalonamento:** última delegação do gestor em `ESCALADA`, com Pendência ainda em `ENTRADA` e sem
  Saída humana posterior ao evento `ESCALADA`;
- **vazio:** nenhuma das quatro categorias possui item; não publica outbox, mesmo que exista
  estimativa histórica.

Os módulos `triagem`, `autonomia` e `metricas` publicam portas de leitura específicas. Todas as
consultas recebem `OrgId` e o identificador do usuário quando aplicável.

## Estimativa

A porta de métricas seleciona eventos humanos de Saída (`RESOLVIDA`, `REPASSADA`, `RESERVADA`,
`REPOUSADA`, `ADIADA`, `INTERROMPIDA`) do próprio usuário nos últimos 90 dias. Intervalos entre
eventos consecutivos maiores que 15 minutos separam sessões e não entram na amostra. Com cinco ou
mais intervalos, calcula-se a mediana, multiplica-se pela quantidade de Pendências distintas do
envelope e arredonda-se para o múltiplo de cinco minutos mais próximo, com mínimo exibido de cinco.
Sem amostra suficiente, `estimativaMinutos` é `null`. O valor nunca é persistido como score pessoal.

## Deliberação de retorno

```text
POST /v1/retornos/{id}/decisao
    { decisao:"APLICAR"|"REJEITAR" }
```

Somente o gestor do alvo pode deliberar. A operação exige `Idempotency-Key`, trava a linha e faz a
transição `OBSERVADO → APLICADO|REJEITADO`. Repetir a mesma decisão é sucesso idempotente; decisão
divergente retorna 409. A operação registra `RETORNO_AVALIADO` na Trilha, com ator humano e somente
identificadores/decisão nos metadados. `APLICAR` significa considerar o retorno na decisão seguinte;
não altera a Pendência nem dispara efeito externo.

## Entrega

O despachante formata um único corpo com seções não vazias, totais excedentes, estimativa opcional e
links absolutos da aplicação. A resolução do destinatário, o fail-closed sem canal e o transporte
Linktor permanecem os do P032. Não há notificação individual de “novo item”, retorno ou
escalonamento no escopo deste pacote.

## Rollout

A migration cria o retrato e índices de leitura, sem duplicar preferências ou jobs. O gerador antigo
é substituído atomicamente pelo compositor novo. Registros antigos da outbox preservam o formato
textual aceito pelo despachante durante a transição; novos registros carregam `resumo_id` e o retrato
estruturado.
