# Cenários — Consulta em linguagem natural

## C1 — "quanto está parado esperando por mim"
- **WHEN** o gestor pergunta "quanto está parado esperando por mim"
- **THEN** a resposta traz a contagem e a soma de `valor_em_jogo` dos itens em
  status `ENTRADA` da sua organização, com os itens listados como fonte.

## C2 — "o que trava por causa do financeiro"
- **WHEN** o gestor pergunta "o que trava por causa do financeiro"
- **THEN** a resposta lista os bloqueios abertos cujo `quem_espera` ou
  `o_que_trava` menciona "financeiro".

## C3 — "o que estou empurrando com a barriga"
- **WHEN** o gestor pergunta sobre itens que vem adiando
- **THEN** a resposta lista os itens abertos com `adiado_count >= 3`.

## C4 — pergunta fora do escopo da fila
- **WHEN** o gestor pergunta algo que nenhum template cobre
- **THEN** a resposta é "não sei responder isso sobre a fila" e **nenhum dado é
  inventado**.

## C5 — isolamento por organização (INV-15)
- **WHEN** duas organizações têm itens
- **THEN** a consulta de uma organização nunca conta nem lista itens da outra.
