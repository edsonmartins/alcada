# Spec — 005 superfície do executor

## Cenário: executor vê apenas as delegações dele
**WHEN** o executor autenticado chama `GET /v1/delegacoes`
**THEN** retorna apenas as delegações onde ele é `dono`
**AND** nunca inclui delegação de outro executor, mesmo na mesma organização

## Cenário: executor registra a proposta
**WHEN** o executor aciona `propor` com um texto de proposta na delegação dele
**THEN** a delegação passa a `PROPOSTA`
**AND** a trilha registra `PROPOSTA_REGISTRADA` com ator `HUMANO:{executor}`
**AND** o item passa a executar por ausência ao fim do prazo+janela se o gestor silenciar

## Cenário: executor conclui a delegação
**WHEN** o executor aciona `concluir` com o resultado
**THEN** a delegação vai para `EXECUTADA` e a pendência para `FECHADA`
**AND** a trilha registra `EXECUTADA` com ator `HUMANO:{executor}`
**AND** um efeito de aviso ao solicitante é enfileirado (só `FECHADA` notifica, INV-09)
**AND** nenhuma execução por ausência ocorre depois (o job de virada vira no-op)

## Cenário: executor devolve ao gestor
**WHEN** o executor aciona `devolver` com um motivo
**THEN** a delegação vai para `DEVOLVIDA` e a pendência volta para `ENTRADA`
**AND** o gestor é avisado com o motivo
**AND** nenhum efeito externo de execução é emitido

## Cenário: executor não age em delegação de outro
**WHEN** um executor tenta `concluir` ou `devolver` uma delegação cujo `dono` é outra pessoa
**THEN** a API responde `403`
**AND** o estado da delegação não muda

## Cenário: ação em estado inválido é recusada
**WHEN** o executor aciona `concluir` numa delegação já `EXECUTADA` ou `DEVOLVIDA`
**THEN** a API responde `409 pendencia.estado_invalido`

## Cenário: o contrato do silêncio é visível
**WHEN** o executor abre uma delegação `N2` com proposta registrada
**THEN** a tela mostra proposta, `nivel`, `prazo` e `janela`
**AND** explica que, no silêncio do gestor, o item executa por ausência ao fim da janela
**AND** explica que, no silêncio de ambos, o item escala ao gestor sem executar

## Cenário: concluir fecha o laço na fila do gestor
**WHEN** o executor conclui uma delegação
**THEN** a pendência não aparece mais em `GET /v1/pendencias?status=ENTRADA`
**AND** o evento de fechamento fica disponível para o canal de origem (pacote 006)
