# Spec — 018 gateway de modelos

## Cenário: provedor sem json_schema estrito falha
**WHEN** uma `TarefaExtracao` com schema estrito é roteada a um provedor sem suporte a `json_schema`
**THEN** a chamada **falha** de forma tratada
**AND** **nunca** degrada para `json_object`
**AND** `require_parameters:true` está presente na requisição que provoca a recusa

## Cenário: indisponibilidade não perde a captura
**GIVEN** `allow_fallbacks:false` e todos os provedores da lista `only` indisponíveis
**WHEN** o gateway esgota as retentativas com backoff dentro da lista
**THEN** a indisponibilidade vira erro tratado, não roteamento fora da lista
**AND** a tarefa é enfileirada em `tarefa_reprocesso`
**AND** o item de captura entra com `confianca = null` e aviso de "extração pendente" na triagem

## Cenário: tenant Soberano nunca sai para o gateway externo
**GIVEN** um tenant com SKU `Soberano`
**WHEN** qualquer tarefa é submetida, independentemente da `Sensibilidade`
**THEN** o destino é sempre local
**AND** nenhuma requisição é emitida ao OpenRouter

## Cenário: classe RESTRITA vai para local sempre
**WHEN** uma tarefa com `Sensibilidade = RESTRITA` (áudio do gestor, valor de contrato, dossiê, avaliação de parceiro) é submetida
**THEN** o destino é local, mesmo em tenant Cloud
**AND** nenhum conteúdo dessas classes atravessa a fronteira externa

## Cenário: minimizador — nenhum identificador direto atravessa a fronteira
**WHEN** uma tarefa `INTERNA` é enviada ao gateway com um corpus real de mensagens
**THEN** o texto que chega ao provedor não contém nome de pessoa, razão social, CPF/CNPJ, telefone, e-mail nem anexo
**AND** nomes aparecem apenas como pseudônimos (`PESSOA_1`, `EMPRESA_2`)
**AND** o teste de vazamento sobre o corpus não encontra nenhum identificador direto

## Cenário: re-hidratação não vaza token entre itens
**GIVEN** dois itens processados com pseudônimos próprios
**WHEN** as respostas são re-hidratadas localmente
**THEN** cada token volta ao valor real do **seu** item
**AND** nenhum token de um item aparece re-hidratado no outro
**AND** o mapa pseudônimo→real não é persistido em lugar algum

## Cenário: política fixa aplicada em toda chamada
**WHEN** qualquer chamada sai para o OpenRouter
**THEN** ela carrega `only`, `allow_fallbacks:false`, `data_collection:deny`, `zdr:true` e `require_parameters:true`
**AND** o chamador não consegue sobrescrever nenhum desses campos
**AND** nenhum plugin/ferramenta do OpenRouter está habilitado

## Cenário: redação indisponível falha visível
**GIVEN** uma `TarefaRedacao` e os provedores homologados indisponíveis
**WHEN** as retentativas se esgotam
**THEN** a tarefa falha de forma visível ao usuário
**AND** **não** degrada para modelo fora da lista homologada

## Cenário: log não contém prompt nem resposta
**WHEN** uma chamada de modelo é registrada em `chamada_modelo`
**THEN** o registro tem tarefa, sensibilidade, provedor efetivo, modelo, tokens, latência, custo e `schema_ok`
**AND** não contém o prompt nem a resposta — apenas `mensagem_id` como referência
**AND** custo de extração e de redação são medidos separadamente

## Cenário: mudança na lista only é evento auditado
**WHEN** a lista `only` de provedores homologados muda
**THEN** a mudança registra uma nova versão auditável (evento contratual)
**AND** o adaptador passa a rotear apenas para a lista vigente
