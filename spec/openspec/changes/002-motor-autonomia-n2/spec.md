# Spec — 002 motor de autonomia N2

## Cenário: execução por ausência
**GIVEN** pendência delegada em `N2` com prazo `T` e janela `PT4H`
**WHEN** ninguém intervém até `T`
**THEN** a delegação vai para `AGUARDANDO_JANELA`
**AND** nenhum efeito externo é emitido
**WHEN** decorre a janela sem intervenção
**THEN** a pendência é fechada como `EXECUTADA_POR_AUSENCIA`
**AND** o efeito externo é publicado no outbox
**AND** a trilha registra prazo, proposta e ausência de intervenções

## Cenário: gestor interrompe antes do prazo
**GIVEN** delegação em `N2` ainda dentro do prazo
**WHEN** o gestor aciona intervir
**THEN** a pendência volta para `ENTRADA`
**AND** o executor é notificado com o motivo
**AND** nenhum efeito externo é emitido

## Cenário: gestor desfaz dentro da janela
**GIVEN** delegação em `AGUARDANDO_JANELA`
**WHEN** o gestor aciona desfazer antes do fim da janela
**THEN** a pendência volta para `ENTRADA`
**AND** a trilha registra `DESFEITA_NA_JANELA`

## Cenário: desfazer fora da janela é recusado
**WHEN** o gestor aciona desfazer após a janela e após publicação do efeito
**THEN** a API responde `409 janela.expirada`
**AND** oferece abrir nova pendência de reversão, não altera a trilha

## Cenário: silêncio de ambos não executa em branco
**GIVEN** delegação em `N2` sem proposta registrada pelo executor
**WHEN** o prazo vence e o tempo de escalonamento decorre
**THEN** a pendência volta para `ENTRADA` marcada como `ESCALADA`
**AND** **não** é executada
**AND** gestor e executor são notificados de que ninguém agiu

## Cenário: classe inelegível não aceita N2
**WHEN** delegação em `N2` é solicitada para classe com `nivel_maximo = N3`
**THEN** a API responde `422 alcada.inelegivel`

## Cenário: gestor ausente
**GIVEN** gestor com registro de ausência vigente
**WHEN** uma nova delegação `N2` é criada
**THEN** o nível é forçado para `N3`
**AND** o executor é notificado da conversão

## Cenário: reinício da aplicação
**GIVEN** job agendado para transição futura
**WHEN** a aplicação é reiniciada
**THEN** o job é recuperado e executado exatamente uma vez
