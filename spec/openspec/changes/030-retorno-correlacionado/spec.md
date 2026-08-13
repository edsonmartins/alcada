# Spec — 030 retorno correlacionado

## C1 — aviso leva token opaco
**WHEN** um aviso que espera retorno é enfileirado
**THEN** existe correlação aleatória ligada à delegação
**AND** metadata enviada ao Linktor não contém IDs de domínio.

## C2 — banco não guarda token claro
**WHEN** a correlação é criada
**THEN** persiste somente SHA-256 e metadados mínimos
**AND** token não aparece na trilha ou logs.

## C3 — token válido correlaciona exatamente
**WHEN** webhook autenticado traz contexto válido do mesmo tenant/canal/autor
**THEN** cria um retorno ligado à delegação
**AND** não cria nova Pendência.

## C4 — ausência de token segue captura normal
**WHEN** a mensagem não traz correlação
**THEN** percorre o pipeline atual sem tentativa heurística.

## C5 — token inválido ou de outro tenant não revela existência
**WHEN** token não resolve na organização da Fonte
**THEN** segue captura normal
**AND** resposta HTTP não diferencia inválido, expirado ou outro tenant.

## C6 — autor divergente é rejeitado
**WHEN** token válido chega de autor/canal diferente do destino
**THEN** não anexa à delegação e segue captura normal
**AND** registra apenas métrica agregada de rejeição.

## C7 — reentrega é idempotente
**WHEN** o mesmo `message.id` é recebido novamente
**THEN** existe um retorno e uma transição no máximo.

## C8 — texto é minimizado
**WHEN** retorno contém PII
**THEN** somente trecho minimizado pode chegar ao classificador
**AND** bruto continua sob retenção do Linktor.

## C9 — classificação não executa delegação
**WHEN** modelo propõe `RESULTADO` ou `PROPOSTA`
**THEN** código não chama concluir/propor nem publica efeito
**AND** item acionável volta à Entrada para confirmação.

## C10 — indisponibilidade é inconclusiva
**WHEN** classificador falha ou confiança é baixa
**THEN** retorno vira `INCONCLUSIVO`, suspende execução automática e volta à Entrada.

## C11 — cobrança não duplica item
**WHEN** retorno é `COBRANCA`
**THEN** incrementa temperatura da Pendência existente
**AND** não cria Pendência ou delegação nova.

## C12 — sem efeito não perturba a fila
**WHEN** retorno é `SEM_EFEITO`
**THEN** fica como evidência sem mudar estados.

## C13 — retorno vence corrida com virada
**WHEN** retorno adquire lock antes da virada N2
**THEN** marca retorno pendente e a virada não publica efeito.

## C14 — virada já publicada não é desfeita silenciosamente
**WHEN** retorno chega após execução firme
**THEN** anexa evidência e oferece compensação
**AND** não reabre nem desfaz automaticamente.

## C15 — retorno aparece no dossiê
**WHEN** gestor abre o item correlacionado
**THEN** vê trecho, origem temporal e tipo proposto com fonte navegável.

## C16 — modo observação não muda estado
**WHEN** tenant está somente em observação
**THEN** correlação e métricas são registradas
**AND** Pendência e delegação permanecem intactas.

## C17 — isolamento integral
**WHEN** duas organizações possuem retornos
**THEN** token, consulta, dossiê e métricas nunca cruzam tenant
**AND** toda query carrega `org_id`.

## C18 — expiração/revogação falha fechada
**WHEN** correlação expirou ou foi revogada
**THEN** mensagem segue captura normal e não é anexada.
