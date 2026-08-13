# Design — 033 consulta completa de itens

## Fatias

1. read model SQL paginado com filtros e busca textual;
2. detalhe com trilha e links profundos;
3. rota web `/itens`, consulta somente;
4. adaptação da consulta natural para referências comuns;
5. testes das cinco perguntas e isolamento.

## Reuso

- `documento_indice.tsv` fornece conteúdo minimizado;
- `ConsultaJdbc` mantém o planejador por whitelist;
- trilha append-only fornece atividade e história;
- rotas `/bloco/{id}`, `/executor` e `/sexta` continuam donas das ações.

## Risco

Joins podem multiplicar Pendências. O read model usa subqueries laterais/última Delegação e conta
separadamente; pagina primeiro por Pendência. Plano de consulta e índices serão exercitados com
volume sintético antes de declarar desempenho.

