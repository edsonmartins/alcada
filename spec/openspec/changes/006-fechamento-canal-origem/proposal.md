# 006 — Fechamento no canal de origem

## Por quê
INV-09: quem pediu recebe estado e fechamento **no canal onde pediu**. O 005 fecha a pendência e
enfileira o efeito `item.fechado`, mas ninguém entrega — hoje o aviso morre no outbox. Sem isto, o
solicitante continua cobrando por outro canal e o backlog percebido não encolhe (a dor da persona
P3). É também o que ativa a entrega real do outbox: até aqui não há `Despachante` de produção.

## O quê
- **Despachante de produção**: consome o outbox e entrega ao canal de origem via **Linktor**
  (ADR-0021). É a implementação que faltava para o worker do outbox sair do no-op.
- **Fechamento ao solicitante**: `item.fechado` vira mensagem no canal de origem ("resolvido"), e a
  resposta de captura (`canal.resposta`) é efetivamente entregue.
- **Trilha de comunicação**: `COMUNICADA` no sucesso; `FALHA_COMUNICACAO` quando a entrega esgota as
  retentativas.
- **Origem na pendência**: a captura passa a guardar canal/destino/thread de origem na pendência, para
  que qualquer transição saiba onde fechar o laço.
- **Fronteira**: ao solicitante vai **estado e fechamento**, nunca deliberação interna, nome de
  decisor ou histórico de terceiros (ADR-0013).

## Fora de escopo
- **Portal externo sem login** para a contraparte (pacote 007).
- **Notificação ao executor/gestor** como superfície própria — aqui os eventos internos continuam no
  outbox; o foco é o **solicitante** no canal de origem.
- **Threading/entrega real do Linktor** — o adaptador é stub até o Linktor expor o canal; o contrato
  `EnviarMensagem` (ADR-0021) é honrado.

## Critério de aceite
- `item.fechado` entrega uma mensagem ao canal de origem do solicitante e grava `COMUNICADA`.
- Reentrega não duplica mensagem (idempotência por `idempotency_key`, dedup no Linktor).
- Falha de entrega é reprocessada; ao esgotar, grava `FALHA_COMUNICACAO`.
- Nenhuma mensagem ao solicitante contém deliberação interna.
- Estados que não são `FECHADA` não disparam fechamento ao solicitante.
