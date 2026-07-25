# ADR-0008 — Três horizontes com capacidade fechada e faixa trimestral blindada

**Status:** aceito · **Data:** 2026-07 · **Relacionado:** INV-08

## Contexto
Reatividade é sintoma de horizonte único: tudo mora no "agora". Sem separação física, o operacional
consome integralmente o tempo de iniciativa própria.

## Decisão
Três horizontes exclusivos: `HOJE`, `SEMANA`, `TRIMESTRE`.

- `SEMANA` tem **capacidade fechada**: atingido o limite, novo compromisso exige remover outro.
- `TRIMESTRE` é **blindado**: agenda reservada que o sistema defende ativamente, medindo invasão e
  **nomeando a causa** ("4h20 consumidas com validação avulsa de integrador").
- Um item pertence a exatamente um horizonte.

## Consequências
- (+) Torna visível o mecanismo de canibalização que o gestor sente mas não consegue nomear.
- (+) Conecta horizonte e alçada: o trimestre só sobrevive se o hoje encolher por delegação.
- (−) Capacidade fechada gera atrito real; será contornada se não houver apoio organizacional.
