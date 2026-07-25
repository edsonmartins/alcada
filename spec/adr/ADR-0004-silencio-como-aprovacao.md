# ADR-0004 — Silêncio como aprovação em N2

**Status:** aceito · **Data:** 2026-07 · **Risco:** alto · **Relacionado:** INV-11, INV-14

## Contexto
N2 é o mecanismo central de encolhimento. Ele inverte o default: em vez de "nada anda sem o gestor",
vira "tudo anda, exceto se o gestor parar". É também o ponto de maior risco cultural e jurídico do
produto.

## Decisão
Adotar execução por ausência, com quatro garantias obrigatórias e não configuráveis para desligar:

1. **Prazo explícito** visível para gestor e executor desde a delegação.
2. **Janela de reversibilidade** após o vencimento, antes de qualquer efeito externo.
3. **Escalonamento** se houver silêncio de ambos: o item não executa em branco, sobe para o gestor
   com aviso de que ninguém agiu.
4. **Trilha imutável** registrando "executado por ausência de intervenção", com prazo, proposta e
   ausência de resposta.

Classes de decisão elegíveis a N2 são configuradas por organização com **valor-limite** e
**escopo**. Decisão irreversível de alto impacto (rescisão, demissão, contrato acima do limite) é
inelegível por padrão.

## Consequências
- (+) Único mecanismo que ataca a raiz da sobrecarga, não o sintoma.
- (+) Cria pressão saudável: quem propõe assume, quem cala aceita.
- (−) Exige contrato jurídico/organizacional explícito; não é decisão técnica.
- (−) Risco de execução indesejada por gestor ausente (férias, doença) — mitigado por modo ausência
  que converte N2 em N3 automaticamente.

## Alternativas rejeitadas
- **Aprovação explícita sempre:** é o estado atual do cliente; não resolve nada.
- **Auto-aprovação sem janela:** inaceitável — erro de transcrição ou classificação vira efeito real.
