# ADR-0009 — LLM propõe, código determinístico executa

**Status:** aceito · **Data:** 2026-07 · **Relacionado:** INV-10, INV-11

## Contexto
O produto usa modelos para extrair, classificar, redigir e sugerir. Decisões gerenciais têm efeito
financeiro e sobre pessoas; inferência não é base aceitável para efeito irreversível.

## Decisão
Separação estrita:

| Camada | Responsável | Exemplos |
|---|---|---|
| Interpretação | LLM | extrair campos, classificar, deduplicar candidato, redigir, sugerir regra |
| Execução | código determinístico | rotear, aplicar alçada, disparar notificação, executar N2, fechar item |

Nenhuma ação com efeito externo é disparada diretamente por saída de modelo. Toda sugestão do
assistente — aceita **ou recusada** — vai para a trilha, porque a recusa é sinal de aprendizado de
alçada.

Respostas do assistente são ancoradas: sem fonte na base, não responde.

## Consequências
- (+) Auditabilidade e previsibilidade; viabiliza discussão jurídica sobre N2.
- (+) Permite trocar de modelo sem alterar comportamento do sistema.
- (−) Menos "mágica" percebida; algumas automações ficam mais rígidas.
