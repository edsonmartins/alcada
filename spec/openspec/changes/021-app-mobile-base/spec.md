# Cenários — App móvel base e sincronização

## C1 — comando despacha uma pendência
- **WHEN** chega `POST /v1/comandos` com `{intencao: RESOLVER, pendenciaId: X}` de uma pendência em ENTRADA
- **THEN** a pendência é fechada (como no `/resolver`) e o resultado do comando é `OK`.

## C2 — sincronização idempotente (INV-13)
- **WHEN** o mesmo `comandoId` é enviado duas vezes (reenvio por instabilidade de rede)
- **THEN** o efeito ocorre **uma só vez** e o segundo envio devolve o resultado gravado, sem re-executar.

## C3 — comando REPASSAR mantém a janela de reversibilidade (INV-14)
- **WHEN** chega `{intencao: REPASSAR, pendenciaId: X, campos:{dono, nivel:N2, prazo}}`
- **THEN** a delegação é criada e **nenhum efeito externo** sai antes do fim da janela (igual ao web).

## C4 — comando sobre pendência que já saiu da fila
- **WHEN** chega um comando para uma pendência que foi fechada/mudou desde a captura offline
- **THEN** o resultado é `IGNORADO` (não `ERRO`), sem alterar nada.

## C5 — CONSULTAR devolve resposta sobre a fila
- **WHEN** chega `{intencao: CONSULTAR, campos:{pergunta:"quanto está esperando por mim"}}`
- **THEN** o resultado traz a resposta da consulta (pacote 020), sem alterar estado.

## C6 — isolamento por organização (INV-15)
- **WHEN** um lote chega com o contexto da organização A
- **THEN** nenhum comando atinge ou consulta dados da organização B.

## C7 — sem reatividade
- **WHEN** um novo item entra na fila do gestor
- **THEN** o app **não** dispara notificação de "novo item" (ADR-0018 / CLAUDE.md §8).
