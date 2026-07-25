# Spec — 007 portal externo sem login

## Cenário: link assinado mostra o estado público
**GIVEN** um token de portal válido, escopado a uma pendência
**WHEN** a contraparte acessa `GET /p/{token}` sem login
**THEN** recebe estado (grosso), data de entrada, prazo previsto e o que falta dela
**AND** a resposta carrega cabeçalho `no-index`

## Cenário: nada de deliberação interna atravessa
**WHEN** a projeção pública é montada
**THEN** ela contém apenas estado, entrada, prazo e o que falta
**AND** não contém título cru, quem espera, classe, nome de decisor nem status interno detalhado
**AND** não contém dado de outra contraparte

## Cenário: token expirado é recusado
**GIVEN** um token cuja `expira_em` já passou
**WHEN** a contraparte acessa `GET /p/{token}`
**THEN** a resposta é `404` (não confirma existência do item)

## Cenário: token revogado é recusado
**GIVEN** um token revogado pelo tenant
**WHEN** a contraparte acessa `GET /p/{token}`
**THEN** a resposta é `404`

## Cenário: token não cruza pendência nem tenant
**GIVEN** um token escopado à pendência A
**WHEN** ele é usado
**THEN** só o estado da pendência A é exposto
**AND** nenhum token revela pendência de outra organização, mesmo com o id em mãos

## Cenário: estado fechado aparece como concluído
**GIVEN** a pendência escopada foi `FECHADA`
**WHEN** a contraparte acessa o portal
**THEN** o estado público é `concluido`
**AND** não é revelado como foi fechada (execução, ausência, delegação)

## Cenário: o token guardado é hash, não o token cru
**WHEN** um token de portal é emitido
**THEN** o banco guarda apenas o hash do token
**AND** o token cru só existe no link devolvido a quem emitiu

## Cenário: o token expira junto com o fechamento, mais folga curta
**GIVEN** um token válido de uma pendência que foi `FECHADA`
**WHEN** a contraparte acessa dentro da folga pós-fechamento
**THEN** vê `concluido`
**WHEN** a folga pós-fechamento decorre
**THEN** o acesso passa a responder `404`
**AND** a URL não continua viva indefinidamente

## Cenário: o que falta é texto curado, não o motivo interno
**GIVEN** um token emitido com `o_que_falta = "documento fiscal assinado"`
**WHEN** a contraparte acessa o portal
**THEN** vê exatamente esse texto
**AND** o campo nunca é derivado do motivo interno de reprovação ou do estado interno
