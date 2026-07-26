# Design — 020 consulta em linguagem natural

## Fluxo
```
pergunta livre
   │
   ▼
gateway.extrair(schema {template, filtro})   ── indisponível/pendente ──┐
   │ (prod: LLM monta a consulta)                                        ▼
   └────────────► template + filtro ◄──────── matcher por palavras-chave (demo)
                        │
                        ▼
        SQL determinístico da whitelist (org_id = ?)   ← INV-15
                        │
                        ▼
        ResultadoConsulta { resposta, itens[], agregado }
```

## Whitelist (`TemplateConsulta`)
| Template | Pergunta-exemplo | Execução |
|---|---|---|
| `ESPERANDO_MIM` | "quanto está parado esperando por mim" | count + soma valor de `status='ENTRADA'` |
| `TRAVADO_POR` | "o que trava por causa do financeiro" | bloqueios abertos com filtro em quem_espera/o_que_trava |
| `AVERSIVOS` | "o que estou empurrando com a barriga" | abertos com `adiado_count >= 3` |
| `DELEGADAS_ABERTAS` | "o que deleguei está aberto?" | `status='DELEGADA'` |
| `POR_CLASSE` | "quantas decisões eu tenho" | count por classe (filtro = classe) |
| `VALOR_TOTAL` | "quanto está em jogo na fila" | soma valor dos abertos |
| `DESCONHECIDO` | (nada casa) | resposta padrão de ausência (C4) |

## Decisões
- **Whitelist fechada, não SQL gerado por LLM.** O LLM devolve só `{template, filtro}`
  validado por schema; o SQL é código nosso. Elimina injeção e alucinação de query.
- **Filtro sempre por bind param** (`ILIKE ?`), nunca concatenado.
- **Sem persistência nova.** Consulta é leitura pura; nenhuma migração.
- **Fonte navegável:** cada item retornado tem `id` → link para o bloco/trilha.
