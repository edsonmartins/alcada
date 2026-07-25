# Spec — 001 captura multicanal

## Cenário: mensagem em grupo declarado vira pendência
**WHEN** chega mensagem em `fonte` ativa mencionando o bot
**AND** o extrator devolve JSON válido com `confianca >= limiar`
**THEN** cria `pendencia` em `ENTRADA` com campos extraídos
**AND** responde no grupo com id e estado
**AND** registra trilha `CRIADA` com ator `SISTEMA:captura`

## Cenário: mensagem irrelevante é descartada
**WHEN** chega mensagem que não menciona o bot, não responde item e não casa com padrão
**THEN** não cria pendência
**AND** registra descarte com motivo, para métrica
**AND** não persiste o texto além da retenção de `evento_bruto`

## Cenário: recobrança não cria item
**WHEN** chega mensagem referenciando entidade de pendência aberta há menos de 7 dias
**AND** similaridade acima do limiar
**THEN** cria `cobranca` vinculada e incrementa `temperatura`
**AND** **não** cria nova pendência
**AND** responde no canal com o estado atual e o prazo previsto

## Cenário: fusão indevida é revertida
**WHEN** o gestor aciona "não é o mesmo item"
**THEN** desvincula a cobrança e cria pendência independente
**AND** registra trilha em ambos os itens
**AND** o par serve de sinal negativo para o limiar

## Cenário: bloqueio operacional não chega ao gestor
**WHEN** chega evento de webhook de monitoramento classificado como `BLOQUEIO`
**AND** existe regra de autonomia ativa para a classe
**THEN** cria pendência já em `DELEGADA` nível `N1` para o dono técnico
**AND** a pendência **não** aparece em `/entrada`
**AND** a trilha registra `ROTEADA_POR_REGRA`

## Cenário: e-mail com pendência enterrada na thread
**WHEN** chega e-mail encaminhado com 9 mensagens
**THEN** o normalizador considera apenas o trecho novo
**AND** a extração aponta o solicitante correto, não o último remetente da thread

## Cenário: varredura completa é impossível
**WHEN** existe fonte ativa de mensageria
**THEN** nenhuma mensagem fora dos critérios de relevância é submetida ao extrator
**AND** o log de filtro permite auditar a proporção processada
