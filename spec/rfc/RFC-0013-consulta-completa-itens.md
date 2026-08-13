# RFC-0013 — Consulta completa de Pendências e decisões

**Status:** implementado · **Implementa:** ADR-0032 · **Pacote:** 033

## Read model

`GET /v1/itens` recebe `q`, `status`, `classe`, `nivel`, `pessoaId`, `de`, `ate`, `origem`, `pagina`
e `tamanho` limitado. A linha reúne Pendência, última Delegação, executor reconhecível, origem,
última transição e quantidade de eventos. Nenhum endereço ou conteúdo bruto é exposto.

Busca textual usa `websearch_to_tsquery('portuguese', ?)` sobre um documento composto por título,
quem espera, o que trava, origem reconhecível, nome do executor e passagens minimizadas do índice.
Filtros são binds ou enums fechados; todas as subqueries incluem `org_id`.

## Detalhe e fontes

`GET /v1/itens/{id}` devolve o resumo e a trilha paginada, com cargas já minimizadas. Cada item
possui `links[] {tipo, href}` calculados deterministicamente: `ENTRADA`, `BLOCO`, `DELEGACAO`,
`REGRA`, `INSTANCIA`, `TRILHA` quando aplicáveis.

## Consulta natural

`POST /v1/consulta` mantém whitelist fechada, mas seus itens passam a devolver `status` e `links`.
Cinco perguntas normativas usam os mesmos filtros/read model; sem fonte, responde ausência.

## Paginação e ordenação

Ordenação fixa: atividade mais recente, depois id. Tamanho padrão 25, máximo 100. A resposta traz
`itens`, `pagina`, `tamanho`, `total`. Não há ordenação escolhida pelo usuário nem visão salva.

## Segurança

Isolamento por `org_id` em consulta principal, joins, subqueries, índice e trilha. Pessoa de outro
tenant é indistinguível de filtro sem resultado. Texto de retorno e mensagem é somente minimizado;
bruto permanece na Linktor.
