# ADR-0030 — Pedido de informação como condição de retorno

**Status:** aceito · **Data:** 2026-08 · **Relacionado:** INV-03/05/09/10/14, ADR-0002/0013/0029

## Contexto

Ao adiar por `INSUMO` ou `TERCEIRO`, hoje a Alçada apenas registra o motivo e uma data. A Pendência
continua parecendo responsabilidade do gestor, embora a próxima ação pertença a outra pessoa.
Transformar isso numa tarefa ou numa caixa de mensagens criaria uma segunda fila.

## Decisão

Um **Pedido de informação** é um subagregado da Pendência: destinatário reconhecível, pergunta
objetiva confirmada por humano, prazo de retorno e estado fechado. Ele não é Pendência nem
Delegação e não possui nível N1/N2/N3.

Ao confirmar o pedido, a mesma transação:

1. registra o pedido e `PEDIDO_INFORMACAO_CRIADO`;
2. coloca a Pendência em `DORMINDO`, com a data de retorno;
3. agenda o despertar persistente;
4. grava na outbox a comunicação, inicialmente reversível.

O envio reutiliza o destinatário nominal e a correlação opaca. A resposta correlacionada é
evidência: marca o pedido como respondido, registra `PEDIDO_INFORMACAO_RESPONDIDO` e devolve a
Pendência à `ENTRADA`. Texto livre nunca conclui a Pendência nem executa efeito.

Só pode existir um pedido aberto por Pendência. Reenvio e resposta são idempotentes. Sem token
válido, a mensagem segue a captura normal, conforme ADR-0029.

## Reversibilidade

A comunicação é efeito externo e permanece represada na outbox durante a janela de
reversibilidade. Desfazer revoga a correlação e cancela logicamente o pedido; registros históricos
permanecem append-only.

## Consequências

- (+) a Entrada representa quem realmente tem a próxima ação;
- (+) retorno não cria Pendência duplicada nem nova fila;
- (+) o gestor confirma destinatário e texto antes do efeito;
- (−) depende do contrato de metadata da Linktor para fechar o ciclo ao vivo;
- (−) pedido interno sem endereço de canal conhecido continua indisponível nesta primeira fatia.

