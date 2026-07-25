# ADR-0024 — Devolução pelo executor é evento próprio na trilha

**Status:** aceito · **Data:** 2026-07 · **Emenda:** ADR-0016 (anexo normativo) · **Relacionado:** INV-11, ADR-0013, pacote 005

## Contexto
O pacote 005 (superfície do executor) introduz a ação **devolver**: o executor olha a delegação e diz
"isto não é comigo" ou "não assumo". O anexo do ADR-0016 é vocabulário fechado e não tem um evento
para isso.

A tentação é reusar `ESCALADA` com um campo `motivo`. **Rejeitada.** `ESCALADA` e devolução são
semanticamente opostas:

- `ESCALADA` = **ninguém agiu**; o item sobe ao gestor por abandono (silêncio de ambos).
- Devolução = o executor **agiu deliberadamente** e recusou a delegação.

Distinguir por campo o que é diferente por natureza contamina a prova. Qualquer consulta que conte
"quantas vezes ninguém agiu" (radar, INV-07) passaria a somar devoluções — e o diagnóstico mentiria
seis meses depois, exatamente onde dói: na trilha e no radar, que são prova.

## Decisão
Acrescentar ao **grupo Autonomia** do anexo normativo do ADR-0016 o tipo:

```
DEVOLVIDA_PELO_EXECUTOR
```

Carga: `delegacao_id`, `motivo`. Ator: `HUMANO:{executor}`. Estado: `DELEGADA → ENTRADA`.

O vocabulário fechado passa de 29 para 30 tipos. Nenhum outro evento muda de significado; nenhuma
consulta existente é afetada, exceto por passar a poder distinguir devolução de escalonamento.

## Consequências
- (+) Radar e trilha separam "ninguém agiu" de "o executor recusou" — métrica honesta.
- (+) Custo de uma linha no anexo e no `CHECK` da tabela.
- (−) Migração da constraint de `trilha` (drop/recreate do `CHECK`), sem impacto em dados existentes.

## Nota
Emendas ao vocabulário fechado seguem este caminho: um ADR curto que altera o anexo do ADR-0016, como
a constituição exige. Reusar um tipo existente para economizar a emenda é barato hoje e caro para
sempre.
