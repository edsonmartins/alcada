# Spec — 012 esteira, checklist e mineração §B

## Cenário: instância aprovada avança sem gerar pendência
**WHEN** uma instância é avaliada e todos os critérios `OBJETIVO` obrigatórios estão `OK` e não há
`JULGAMENTO` pendente
**THEN** o desfecho é `APROVADA`
**AND** a instância avança para a próxima etapa (ou `CONCLUIDA` se for a última)
**AND** **nenhuma pendência** é gerada para o gestor

## Cenário: falha objetiva gera pendência com resultado anexado
**WHEN** um critério `OBJETIVO` obrigatório `FALHOU`
**THEN** o desfecho é `REPROVADA`
**AND** é criada uma pendência classe `ESTEIRA` em `ENTRADA` com o resultado da avaliação anexado
**AND** a trilha da pendência registra `CAPTADA`
**AND** a instância permanece na etapa até `avancar`

## Cenário: julgamento pendente gera pendência
**WHEN** não há falha objetiva mas há critério/apontamento de `JULGAMENTO`
**THEN** o desfecho é `PENDENTE_JULGAMENTO` e uma pendência `ESTEIRA` é gerada

## Cenário: checklist é versionado, nunca sobrescrito
**WHEN** o gestor publica um novo checklist para a etapa
**THEN** uma nova `versao` é criada (a anterior permanece)
**AND** avaliações antigas continuam referenciando a versão vigente à época

## Cenário: mineração propõe critério objetivo recorrente
**WHEN** um apontamento `OBJETIVO` aparece em ≥ 50% das reprovações da etapa (janela e mínimo)
**THEN** `GET /v1/esteiras/{id}/checklist/propostas` o traz como critério candidato
**AND** não o propõe se já for critério da versão vigente

## Cenário: julgamento não vira checklist
**WHEN** um apontamento é do tipo `JULGAMENTO`
**THEN** ele aparece numa lista **separada** de "critérios de julgamento", nunca como candidato a
critério objetivo

## Cenário: poucas reprovações não propõem
**WHEN** a etapa teve menos reprovações que o mínimo
**THEN** nenhum critério é proposto (ruído não vira checklist)

## Cenário: aceite cria versão, não promove sozinho (INV-10)
**WHEN** o gestor aceita critérios propostos via `POST /v1/esteiras/{id}/checklist`
**THEN** uma nova versão de checklist é criada com esses critérios
**AND** nenhum critério é adicionado sem esse aceite

## Cenário: isolamento por organização (INV-15)
**WHEN** dois tenants têm esteiras
**THEN** instâncias, avaliações e propostas de um nunca aparecem no outro
**AND** todo predicado carrega `org_id`
