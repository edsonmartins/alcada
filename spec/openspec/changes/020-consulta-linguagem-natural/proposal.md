# 020 — Consulta em linguagem natural

**Fase:** F4 · **Implementa:** RFC-0004 §3 · **Depende de:** 018 (gateway)

## Problema
O gestor sabe perguntar em voz alta ("quanto está parado esperando por mim?",
"o que trava por causa do financeiro?"), mas hoje só consegue essa resposta
navegando filtros. Falta a tradução de pergunta livre → resposta sobre a fila.

## Proposta
Consulta em linguagem natural **determinística** (RFC-0004 §3): **não é RAG
livre**. O modelo apenas escolhe, dentro de uma **whitelist de consultas
parametrizadas**, qual se aplica e com que filtro; a execução é SQL determinístico
sobre o modelo relacional, escopado por `org_id` (INV-15); a resposta é montada
a partir do resultado, sempre com os itens como fonte navegável.

- **INV-10:** o LLM propõe a consulta; nada executa por inferência — quem roda a
  consulta é código, sobre uma lista fechada de templates.
- **Degradação (piloto):** no profile demo o gateway é stub. Um classificador
  determinístico por palavras-chave cobre os casos do RFC, para a consulta ser
  demonstrável sem LLM real. Pergunta fora da whitelist → "não sei responder isso
  sobre a fila" (nunca inventa).

## Não-objetivos
- RAG sobre documentos/anexos (isso é o dossiê, pacote 014).
- Consulta que atravessa organizações. Escopo sempre por `org_id`.
- Executar ações a partir da consulta (só leitura).
