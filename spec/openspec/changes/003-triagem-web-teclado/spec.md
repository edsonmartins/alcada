# Spec — 003 triagem web por teclado

## Cenário: resolver fecha o item e avisa o solicitante
**WHEN** o gestor aciona `resolver` em uma pendência em `ENTRADA`
**THEN** a pendência vai para `FECHADA`
**AND** a trilha registra `RESOLVIDA` com ator `HUMANO:{gestor}`
**AND** um efeito `item.fechado` é enfileirado no outbox (só `FECHADA` notifica)

## Cenário: reservar agenda e continua dependente do gestor
**WHEN** o gestor aciona `reservar` com `agendado_para`
**THEN** a pendência vai para `AGENDADA` com o horário
**AND** a trilha registra `RESERVADA`
**AND** o item continua contando como dependente do gestor para a métrica

## Cenário: repousar adormece e desperta na data
**WHEN** o gestor aciona `repousar` com `volta_em`
**THEN** a pendência vai para `DORMINDO`
**AND** a trilha registra `REPOUSADA`
**WHEN** chega a data `volta_em`
**THEN** a pendência volta para `ENTRADA`
**AND** a trilha registra `DESPERTADA`
**AND** o despertar é idempotente por `(pendencia_id, transicao, ocorrencia)`

## Cenário: adiar exige data e o que falta
**WHEN** o gestor aciona `adiar` sem `volta_em`
**THEN** a API responde `400` e nada transiciona
**WHEN** aciona `adiar` com `o_que_falta` fora de `{NADA, INSUMO, TERCEIRO}`
**THEN** a API responde `422`
**WHEN** aciona `adiar` com `volta_em` e `o_que_falta` válidos
**THEN** `adiado_count` é incrementado
**AND** a trilha registra `ADIADA`

## Cenário: adiar responde diferente a cada motivo
**WHEN** o gestor adia com `o_que_falta = NADA`
**THEN** a resposta oferece abrir bloco de decisão ("não está bloqueado, está evitado")
**WHEN** adia com `TERCEIRO`
**THEN** a resposta oferece repassar para quem tem a bola
**WHEN** adia com `INSUMO`
**THEN** o sistema passa a cobrar o insumo, não a lembrar o item

## Cenário: Hoje mostra no máximo três
**WHEN** `GET /v1/hoje` é chamado com muitos itens elegíveis
**THEN** retorna no máximo 3 itens
**AND** cada item traz a justificativa de por que está ali

## Cenário: triagem inteira pelo teclado
**WHEN** o foco está na lista de entrada
**THEN** `j`/`k` movem o cursor, `Enter` abre o item
**AND** `1`/`2`/`3`/`4` aplicam Resolver/Repassar/Reservar/Repousar
**AND** `a` inicia o adiar como ação secundária, nunca como portão

## Cenário: não existe arrastar
**WHEN** a interface de triagem é renderizada
**THEN** nenhum elemento é arrastável entre listas ou colunas
**AND** não há campo livre obrigatório nem taxonomia editável pelo usuário

## Cenário: ação em lote
**WHEN** o gestor seleciona vários itens e aplica `resolver` (ou `repousar`)
**THEN** todos transicionam em uma ação
**AND** cada um gera sua própria trilha
**AND** a janela de desfazer vale para o lote

## Cenário: sessão improdutiva é nomeada
**WHEN** houve tempo de uso da triagem sem nenhuma transição de estado
**THEN** o sistema sinaliza a sessão improdutiva ao gestor
**AND** não apresenta a lista como algo a "organizar"

## Cenário: desfazer dentro da janela
**WHEN** o gestor aciona uma saída e desfaz dentro da janela
**THEN** a pendência volta ao estado anterior
**AND** nenhum efeito externo foi emitido (mutação otimista, INV-14)
