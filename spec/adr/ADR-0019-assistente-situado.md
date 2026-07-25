# ADR-0019 — Assistente situado, não chat onipresente

**Status:** aceito · **Data:** 2026-07 · **Relacionado:** ADR-0009, RFC-0004

## Contexto
O reflexo padrão é adicionar uma bolha de chat. Para este usuário isso seria mais uma caixa de
entrada — e uma que exige digitar. Ele já tem duas cheias.

## Decisão
O assistente aparece **dentro de um contexto**, com estado pré-carregado, em quatro momentos:

| Momento | Função | Superfície |
|---|---|---|
| Antes de decidir | dossiê ancorado com fonte clicável | bloco de decisão (web) |
| Depois de decidir | redigir o retorno, com variações de tom | bloco de decisão (web) |
| Após a decisão | **uma** pergunta de aprendizado, nunca duas | onde o gestor estiver |
| Consulta | linguagem natural sobre a fila estruturada | web e voz |

Proibido: chat aberto na home; resposta sem fonte; sugestão de autonomia sem evidência clicável
(os N casos, navegáveis).

A pergunta de aprendizado tem três respostas: `sim` (cria regra), `agora não`, `não perguntar isso`
(silencia a classe permanentemente).

## Consequências
- (+) A IA se paga no momento aversivo — o custo do item difícil é escrever, não decidir.
- (+) O laço de aprendizado transforma critério tácito em regra explícita, que é o ativo do produto.
- (−) Menos demonstrável em venda que um chat; exige demo guiada.
