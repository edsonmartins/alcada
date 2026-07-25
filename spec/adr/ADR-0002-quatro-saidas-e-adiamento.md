# ADR-0002 — Quatro saídas fixas e adiamento de primeira classe

**Status:** aceito · **Data:** 2026-07 · **Relacionado:** INV-05

## Contexto
Triagem com muitas opções exige comparação e trava o gestor. Triagem com poucas opções que não
cobrem o comportamento real (adiar) é contornada — o gestor volta ao e-mail.

## Decisão
Uma pergunta ("isso precisa mesmo de você?") e quatro saídas fixas: **Resolver, Repassar, Reservar,
Repousar**. Sem estimativa, sem etiqueta, sem prioridade manual.

**Adiar** existe como ação de primeira classe, fora do conjunto de quatro, exigindo:
1. `volta_em` (data obrigatória; "depois" não é aceito)
2. `o_que_falta` ∈ {NADA, INSUMO, TERCEIRO}

O sistema responde diferente a cada valor:
- `NADA` → oferece bloco de decisão ("não está bloqueado, está evitado")
- `TERCEIRO` → oferece repassar para quem tem a bola
- `INSUMO` → passa a cobrar o insumo, não a lembrar do item

## Consequências
- (+) Triagem sobrevive sem tela — habilita canal de voz (ADR-0014).
- (+) O contador de adiamentos vira o sinal diagnóstico mais forte do sistema.
- (−) Casos que não cabem nas quatro saídas precisam ser forçados; aceitamos a perda de fidelidade.
- (−) Risco de o adiamento virar caminho de menor esforço; mitigado pela fricção das duas perguntas.

## Alternativas rejeitadas
- **Adiar como quinta saída (5R):** dilui a pergunta e legitima o adiamento como decisão. Ele é
  ausência de decisão e a interface deve refletir isso — botão secundário, não portão.
- **Snooze silencioso:** é exatamente o comportamento do e-mail que causa o problema.
