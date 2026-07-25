# Spec — 013 bloco de decisão

## Cenário: dossiê montado dos dados do item, com fonte
**WHEN** o gestor abre `GET /v1/pendencias/{id}/bloco`
**THEN** recebe os fatos do item (quem espera, o que trava, valor, prazo, cobranças) e as opções da
classe, cada opção com sua consequência
**AND** a trilha do item continua disponível como fonte navegável (RFC-0004)

## Cenário: redação é rascunho editável (INV-10)
**WHEN** o gestor pede `redigir` com uma opção e um tom
**THEN** recebe um rascunho editável
**AND** nada é enviado a ninguém só por pedir o rascunho

## Cenário: sem modelo, degrada com honestidade
**WHEN** o gateway de modelos não está disponível (profile demo / stub)
**THEN** `redigir` devolve `disponivel=false` com um rascunho-esqueleto editável
**AND** o dossiê e o decidir continuam funcionando

## Cenário: decidir fecha e enfileira a comunicação
**WHEN** o gestor aciona `decidir` com opção e texto
**THEN** a pendência vai para `FECHADA`
**AND** a trilha registra `DECIDIDA_NO_BLOCO` com ator humano
**AND** a comunicação é enfileirada no outbox (o envio ao canal é efeito de outbox)

## Cenário: não decide por inferência (INV-10)
**WHEN** o bloco é montado ou a redação é gerada
**THEN** nenhuma transição de estado ocorre e nenhum efeito externo é enfileirado
**AND** só `decidir` (ação do gestor) fecha a pendência e enfileira o efeito

## Cenário: decidir item já fechado é recusado
**WHEN** o gestor aciona `decidir` numa pendência já `FECHADA`
**THEN** a API responde `409`

## Cenário: isolamento por organização (INV-15)
**WHEN** o bloco é pedido para uma pendência
**THEN** só é montado se a pendência é da organização do contexto
**AND** todo predicado carrega `org_id`
