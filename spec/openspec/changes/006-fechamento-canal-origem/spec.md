# Spec — 006 fechamento no canal de origem

## Cenário: item fechado avisa o canal de origem
**GIVEN** uma pendência com origem de canal registrada (canal, destino)
**WHEN** ela é fechada e o efeito `item.fechado` é processado pelo worker do outbox
**THEN** o Despachante envia uma mensagem de fechamento ao canal de origem via Linktor
**AND** a trilha registra `COMUNICADA` com ator `SISTEMA:motor:notificacao`

## Cenário: só o solicitante é avisado, sem deliberação interna
**WHEN** a mensagem de fechamento é montada
**THEN** ela contém estado e fechamento
**AND** não contém deliberação interna, nome de decisor nem histórico de terceiros (ADR-0013)

## Cenário: reentrega não duplica mensagem
**WHEN** o mesmo `item.fechado` é processado mais de uma vez
**THEN** o canal recebe a mensagem uma única vez (idempotência por `idempotency_key`)
**AND** não há segunda `COMUNICADA` para o mesmo efeito

## Cenário: falha de entrega é reprocessada e, ao esgotar, registrada
**GIVEN** o canal indisponível
**WHEN** a entrega falha
**THEN** o worker do outbox reprocessa com backoff
**WHEN** as retentativas se esgotam
**THEN** o efeito vai para `ERRO`
**AND** a trilha registra `FALHA_COMUNICACAO`

## Cenário: eventos internos não vão ao solicitante
**WHEN** o worker processa `delegacao.executada`, `delegacao.escalada` ou `delegacao.devolvida`
**THEN** nenhuma mensagem é enviada ao canal do solicitante
**AND** nenhuma `COMUNICADA` de fechamento é gerada por esses eventos

## Cenário: resposta de captura é entregue no canal
**WHEN** a captura enfileira `canal.resposta` com id e estado do item
**THEN** o Despachante entrega o texto no canal de origem

## Cenário: item sem canal de origem não quebra
**GIVEN** uma pendência criada por escape manual, sem origem de canal
**WHEN** `item.fechado` é processado
**THEN** o Despachante ignora sem erro (não há canal para fechar)
**AND** o efeito é marcado como entregue (não fica preso em retentativa)
