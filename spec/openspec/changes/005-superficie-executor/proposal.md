# 005 — Superfície do executor

## Por quê
O 002 entregou o motor de autonomia, mas N2 **sem tela do executor é mecanismo sem apoio** (PRODUTO
§8). Quem recebe a delegação precisa de um contrato claro (ADR-0013, INV-09): o que foi proposto, até
quando vale, e o que acontece se ninguém agir. Sem isso o executor fica bloqueado sem saber se pode
agir — exatamente a dor da persona P2.

## O quê
- **API do executor**: `concluir` e `devolver` (o `propor` e o `GET /v1/delegacoes` já vieram no 002),
  com autorização por dono — o executor só age nas delegações dele.
- **Contrato visível**: cada delegação mostra proposta, prazo, janela de reversibilidade e o que
  ocorre no silêncio (escalonamento / execução por ausência).
- **Tela do executor** (web): lista das delegações do usuário autenticado, com nível, prazo e as três
  ações (propor, concluir, devolver).
- **Fechamento do laço**: concluir fecha a pendência e avisa o solicitante (INV-09); devolver sobe ao
  gestor com motivo declarado.

## Fora de escopo
- **Solicitante e contraparte externa** (portal sem login) — pacote 007.
- **Fechamento no canal de origem** (Linktor) — pacote 006; aqui a notificação é efeito de outbox.
- **Redação assistida da conclusão** (assistente) — pacotes 012/013.

## Critério de aceite
- O executor vê **apenas** as delegações onde é dono; nunca as de outro.
- `concluir` fecha a pendência (`FECHADA`), grava trilha e enfileira aviso ao solicitante.
- `devolver` devolve à `ENTRADA` com motivo, e avisa o gestor.
- Agir em delegação de outro dono é recusado (`403`).
- Ação em estado inválido é recusada (`409`).
