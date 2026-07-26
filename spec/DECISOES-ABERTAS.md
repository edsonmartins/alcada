# Decisões em aberto (gates)

Itens que bloqueiam ou condicionam decisões maiores. Cada um deve virar ADR ou ser encerrado.

## G1 — Nome do produto  ✅ **encerrado**
**Alçada**, domínio `alcada.app`. Registrado em ADR-0022, com as convenções de uso que evitam colisão
com o termo de domínio. Pendências remanescentes: registro no INPI (classes 9 e 42) e decisão sobre
domínios defensivos (`alcada.com.br`, `alcada.io`).

## G2 — Aceitação de N2 pelo gestor-piloto  **(bloqueante)**
O produto inteiro depende de "silêncio vale como aprovação". Validar em F0, no papel.
Se não houver aceitação: o produto entrega ~30% e escopo/preço precisam ser recalibrados no discovery.

## G3 — Objetividade do critério de validação de integrador
Duas perguntas ao cliente-piloto:
1. o critério é técnico e objetivo, ou envolve julgamento comercial caso a caso?
2. o gargalo é **validar** ou é **entender o que a contraparte enviou**?

Se for (2), a alavanca é o formato de submissão e a esteira muda de desenho (ADR-0012).

## G4 — Motor de STT/TTS on-device  → **ADR-0026 (proposto)**
Decisão proposta: STT on-device atrás de porta, baseline Whisper `small` (MIT) + fallback de
plataforma; TTS nativo da plataforma para a confirmação; Supertonic reservado. Mantém-se o **gate de
avaliação PT-BR com ruído de carro** antes da POC. Falta ratificar o limiar de WER de negócio.

## G5 — Base legal e DPIA
RIPD formal para captura de canais antes do primeiro cliente em produção (ADR-0011).

## G6 — Detecção de movimento  → **ADR-0027 (proposto)**
Decisão proposta: modo trajeto é estado explícito com **override manual sempre disponível**; sinais
(CarPlay/Android Auto, APIs de atividade do SO, declaração manual) só **ligam** o modo, nunca
destravam sozinhos; viés de projeto = preferir bloquear a permitir; sem rastreamento. Falta ratificar.

## G8 — Segmentação de oferta: Cloud x Soberano  **(comercial, urgente)**
ADR-0020 tornou impossível vender "soberania de dados" no SKU que usa OpenRouter. Definir as duas
ofertas, o preço de cada uma e o discurso antes da primeira proposta comercial. Não há roteamento
in-region no Brasil pelo gateway; residência nacional só existe no SKU Soberano.

## G7 — Recall mínimo aceitável da captura
Definir o número **de negócio** antes de produção. Abaixo dele, o produto não vai ao ar.

**Valores provisórios adotados para destravar o pacote 001** — são hipótese, não decisão de negócio,
e devem ser revistos com o primeiro dado real:

| Métrica | Provisório | Racional |
|---|---|---|
| Recall da captura | **≥ 85%** contra baseline manual | abaixo disso o gestor não confia e volta ao e-mail |
| Precisão da classe `BLOQUEIO` | **≥ 95%** | classificar decisão como bloqueio a manda para execução técnica — erro caro |
| Uso do escape manual | **< 10%** das entradas | acima disso a captura está inadequada (ADR-0005) |
| Limiar de deduplicação | similaridade **≥ 0,82** + mesma entidade + janela 7d | conservador de propósito: fundir errado esconde pendência, não fundir só duplica |

O limiar de dedup é por tenant e começa alto. Preferimos duplicata visível a fusão silenciosa.

## G9 — Lista de provedores homologados
Fechar a lista `only` do OpenRouter e registrá-la como anexo de suboperadores no contrato e no RIPD.
Mudança na lista passa a ser evento contratual (RFC-0007).

**Para destravar o pacote 018:** comece com **um único provedor fixado**, escolhido por você. O
adaptador é escrito contra a lista, não contra um nome — acrescentar o segundo é configuração, não
código. A decisão de quais provedores é sua e é contratual; a implementação não espera por ela.
