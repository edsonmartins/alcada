# Spec — 004 trilha imutável

## Cenário: transição de estado gera evento append
**WHEN** um módulo registra uma transição de pendência pela porta da trilha
**THEN** um novo evento é inserido com `ator`, `ocorrido_em`, `estado_anterior` e `estado_posterior`
**AND** o evento participa da transação do chamador — se ela reverte, o evento não existe

## Cenário: UPDATE na trilha é rejeitado
**WHEN** qualquer conexão tenta `UPDATE` em uma linha de `trilha`
**THEN** o banco rejeita a operação pelo trigger append-only
**AND** o rejeite ocorre mesmo para role com privilégio (não depende só do `REVOKE`)

## Cenário: DELETE na trilha é rejeitado
**WHEN** qualquer conexão tenta `DELETE` em `trilha`
**THEN** o banco rejeita a operação pelo trigger append-only

## Cenário: tipo fora do vocabulário fechado é rejeitado
**WHEN** uma escrita usa um `tipo` que não está nos 29 do anexo do ADR-0016
**THEN** a escrita falha pela `CHECK` constraint
**AND** nenhum módulo consegue introduzir um tipo novo sem emenda ao ADR

## Cenário: ator fora do formato é rejeitado
**WHEN** uma escrita informa `ator` que não casa `HUMANO:…`, `SISTEMA:{motor|regra}:…` ou `ASSISTENTE:…@…`
**THEN** a escrita falha pela `CHECK` de formato

## Cenário: correção não altera o original
**WHEN** um evento precisa ser corrigido
**THEN** grava-se um novo evento `COMPENSACAO` referenciando `evento_compensado_id`
**AND** o evento original permanece inalterado e legível
**AND** não existe nenhum caminho de `UPDATE` para a trilha

## Cenário: descarte por irrelevância não gera trilha
**WHEN** a captura descarta uma mensagem por irrelevância
**THEN** nenhum evento de trilha é criado (não há pendência ainda)
**AND** o descarte é contabilizado na métrica de captura, com motivo

## Cenário: execução por ausência registra carga completa
**WHEN** o motor de autonomia fecha uma pendência por ausência
**THEN** o evento `EXECUTADA_POR_AUSENCIA` carrega `delegacao_id`, `prazo`, `proposta`, `janela` e `intervencoes`
**AND** a lista de intervenções reflete que ninguém interveio

## Cenário: consulta de trilha isolada por organização
**WHEN** `GET /v1/pendencias/{id}/trilha` é chamado por um usuário da organização `A`
**THEN** retorna apenas eventos daquela pendência, em ordem cronológica
**AND** nunca retorna evento de pendência de outra organização (INV-15)

## Cenário: rolagem de partição mensal antes do uso
**WHEN** o job de rolagem executa
**THEN** a partição do mês seguinte existe antes de qualquer escrita naquele mês
**AND** o job é idempotente: reexecutar não recria nem falha
**AND** se a partição `DEFAULT` receber linhas, um alerta é emitido

## Cenário: eliminação LGPD preserva a cadeia
**WHEN** exercido o direito de eliminação de um titular
**THEN** o registro do titular em `identidade` é pseudonimizado
**AND** as referências por id na trilha permanecem válidas
**AND** a sequência de eventos continua íntegra e auditável, sem nenhum `UPDATE` na trilha
