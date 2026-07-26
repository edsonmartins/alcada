# 023 — Modo trajeto: condução por voz, recusa por classe e janela de trajeto

**Fase:** F5 · **Implementa:** ADR-0014 · RFC-0005 · **Honra:** INV-14, INV-10
**Depende de:** 021 (base), 022 (voz)

## Problema
Em movimento, o risco muda: decidir algo grave dirigindo é perigoso e erode
confiança. O modo trajeto conduz o despacho por voz **com segurança** — recusa
o que não cabe no trânsito e represa efeitos externos até estacionar.

## Proposta
1. **Sequência conduzida pelo sistema** (não navegação pelo usuário): um item por
   vez; item que não cabe em ~8s de fala não entra no trajeto.
2. **Recusa por classe em movimento** (ADR-0014 §3): decisões de alto impacto/
   irreversíveis são recusadas em trânsito, com justificativa e **bloco
   agendado**: *"Esse é o de embalagem. Separei para quinta, com o dossiê."*
   Parado, tudo é liberado.
3. **Janela de reversibilidade de trajeto** (ADR-0014 §4, INV-14): efeitos
   externos **não disparam** durante o deslocamento; ficam represados.
4. **Resumo de trajeto ao estacionar**: lista o que foi despachado, com
   **desfazer por item**, antes de comunicar terceiros.
5. **Integrações**: CarPlay/Android Auto, áudio em background, Live Activity/
   notificação persistente **durante o trajeto** (não é push de "novo item").

## Não-objetivos
- STT/interpretação (022) e sync (021) — reusados.
- Definir a fonte de detecção de movimento (sensor × veículo × manual) — decisão
  pendente do RFC-0005; este pacote trata o **sinal de movimento** atrás de uma porta.
