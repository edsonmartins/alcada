# Spec — 036 repasse interno com link e toque dirigido

## C1 — link abre o alvo autenticado
**WHEN** um usuário autenticado abre o link de uma Pendência acessível
**THEN** Android, iOS ou web mostram diretamente o estado atual daquela Pendência.

## C2 — UUID não concede acesso
**WHEN** o link pertence a outra organização ou pessoa sem acesso
**THEN** a API não entrega conteúdo e o app mostra indisponibilidade sem revelar dados.

## C3 — repasse interno produz reforço, não entrada genérica
**WHEN** uma Pendência é delegada a uma pessoa interna
**THEN** existe no máximo um reforço por canal para aquela delegação; capturas e registros comuns não geram push.

## C4 — janela e trajeto represam todos os canais
**WHEN** o repasse ainda pode ser desfeito ou o trajeto não foi liberado
**THEN** nenhum WhatsApp ou push é entregue ao executor.

## C5 — clique não decide
**WHEN** o executor abre o link ou toca no push
**THEN** apenas visualiza a Pendência; nenhuma aceitação, execução ou mudança de nível é inferida.

## C6 — dispositivo é escopado e revogável
**WHEN** um token é registrado ou removido
**THEN** a operação afeta somente a organização, pessoa e instalação autenticadas.

## C7 — canais degradam separadamente
**WHEN** FCM, APNs ou WhatsApp está indisponível
**THEN** os demais canais continuam e a delegação permanece visível na fila.

## C8 — nenhuma PII aparece no link ou log
**WHEN** o reforço é composto e entregue
**THEN** telefone e token não aparecem na URL, payload de Trilha ou logs de aplicação.
