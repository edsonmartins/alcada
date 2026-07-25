# ADR-0017 — Métrica comportamental só com ação associada

**Status:** aceito · **Data:** 2026-07 · **Relacionado:** INV-07

## Contexto
O sistema mede adiamentos, tempo parado e percentual de gargalo. Esses números descrevem
comportamento pessoal do gestor. Apresentados como placar, o produto morre na segunda semana — e com
razão.

## Decisão
Toda superfície que exponha padrão pessoal deve, na mesma tela:

1. **oferecer saída acionável** (resolver, soltar, marcar bloco, promover alçada)
2. **nomear a causa provável**, não o desvio ("4 destes envolvem dizer não a alguém")
3. **evitar linguagem de desempenho**: sem score, sem ranking, sem streak, sem comparação entre pessoas

Proibido: exportar métrica de comportamento individual do gestor para superior hierárquico ou RH.
Métricas agregadas de organização são permitidas sem atribuição individual.

## Consequências
- (+) Preserva a relação de confiança que o produto exige para funcionar.
- (−) Limita features de "engajamento" e relatórios que clientes eventualmente pedirão.
