# Design — 027 saneamento do estado do produto

## Fonte e precedência

O saneamento lê código, testes e `tasks.md`, mas não altera a precedência constitucional. O índice é
uma projeção; o detalhe de uma pendência permanece no pacote correspondente.

## Modelo de maturidade

Os estados são cumulativos: especificado → escrito → testado localmente → validado ao vivo →
adotado. Capacidades parciais recebem o menor estado honesto do fluxo completo e explicitam a
pendência principal.

## Manutenção

`docs/ESTADO-PRODUTO.md` concentra a fotografia. README mantém apenas o resumo. Todo pacote futuro
atualiza ambos quando houver mudança material de maturidade.
