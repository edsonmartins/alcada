# Spec — 031 pedido estruturado de informação

## C1 — confirmação cria pedido e repousa
**WHEN** gestor confirma contato, pergunta e prazo válidos
**THEN** cria um pedido aberto, repousa a Pendência e agenda prazo.

## C2 — comunicação usa outbox e correlação
**WHEN** a janela reversível é liberada
**THEN** envia uma vez pelo Linktor com correlação opaca.

## C3 — resposta desperta
**WHEN** chega resposta correlacionada do autor esperado
**THEN** pedido vira respondido, Pendência volta à Entrada e a evidência é auditada.

## C4 — resposta repetida é idempotente
**WHEN** o mesmo `message.id` é reentregue
**THEN** existe um retorno e uma transição no máximo.

## C5 — prazo desperta sem resposta
**WHEN** o prazo vence antes do retorno
**THEN** pedido vira vencido e Pendência volta à Entrada com ação explícita.

## C6 — retorno vence corrida
**WHEN** resposta adquire lock antes do job de prazo
**THEN** o job posterior não altera o pedido respondido.

## C7 — prazo vence corrida
**WHEN** job adquire lock antes da resposta
**THEN** a resposta permanece evidência, sem segunda transição de estado.

## C8 — pedido aberto não duplica
**WHEN** tentam criar outro pedido para a mesma Pendência
**THEN** rejeita com conflito e não envia outra comunicação.

## C9 — isolamento por organização
**WHEN** contato ou Pendência pertence a outro tenant
**THEN** o comando não revela nem usa o recurso.

## C10 — texto livre não executa
**WHEN** a resposta diz “concluído” ou contém instrução
**THEN** apenas desperta para avaliação humana; não fecha a Pendência.

