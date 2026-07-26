# ADR-0026 — Motor de STT/TTS on-device para o canal de voz

**Status:** proposto (aguarda ratificação) · **Data:** 2026-07 · **Encerra:** G4 (DECISOES-ABERTAS)
**Relacionado:** ADR-0014, INV-13, ADR-0010 · **Condiciona:** pacote 022

## Contexto
O canal de voz (ADR-0014) exige transcrição **on-device**: perder a fala custa a adoção (INV-13) e o
conteúdo é sensível (ADR-0010 — não pode sair para um STT em nuvem sem passar pela política do
gateway). A qualidade precisa aguentar **PT-BR com ruído de carro**. O TTS serve só para a **frase de
confirmação de campos críticos** ("Alexandre, N2, até sexta. Confirma?") — não é leitura longa.

Restrições:
1. **Offline obrigatório** (INV-13): sem rede no túnel, no elevador, no estacionamento.
2. **Sensibilidade** (ADR-0010): áudio/transcrição de decisão não vão para nuvem fora da política.
3. **Tamanho e bateria**: o modelo roda no aparelho do gestor; app não pode inchar sem limite.
4. **Licença**: precisa ser distribuível comercialmente (Cloud e Soberano).

## Decisão

### 1. STT on-device, atrás de uma porta
A transcrição é sempre local. O motor fica atrás da porta do pacote 022 (`Stt`), plugável — trocar o
motor é configuração, não reescrita.

### 2. Baseline recomendado: família Whisper (whisper.cpp), modelo `small` multilíngue
- **Licença MIT**, distribuível nos dois SKUs.
- Qualidade PT-BR reconhecidamente boa; `small` equilibra acurácia × tamanho (~466 MB quantizável
  para ~150–250 MB) × latência aceitável para frases curtas.
- **Fallback**: STT on-device da plataforma (iOS on-device dictation / Android `SpeechRecognizer`
  offline) onde disponível, para aparelhos que não comportem o modelo — atrás da mesma porta.

### 3. TTS: nativo da plataforma para a confirmação
- iOS `AVSpeechSynthesizer` / Android `TextToSpeech` — offline, sem custo, sem licença extra,
  qualidade suficiente para uma frase de confirmação.
- **Supertonic** (MIT/OpenRAIL-M) e TTS neural ficam reservados para reavaliação **se e quando** a
  naturalidade da voz virar requisito — não é gate do 022.

### 4. Gate de avaliação antes da POC (obrigatório)
Antes de qualquer POC de voz, medir num corpus PT-BR com ruído de carro:
- **WER** (word error rate) do baseline vs. fallback de plataforma;
- **WER dos campos críticos** (nomes próprios, valores, prazos) — é onde o erro custa (ADR-0014 §2);
- **latência** por frase e **tamanho** final do app por plataforma;
- **bateria** num trajeto de ~30 min.
Se o baseline não passar num limiar de negócio (a definir com o piloto), reabrir a escolha.

## Alternativas consideradas
- **STT em nuvem** (Whisper API, Google/Deepgram): melhor acurácia, mas **viola INV-13** (offline) e a
  **sensibilidade** (ADR-0010). Recusado para o caminho principal.
- **Vosk** (Apache-2.0, muito leve): menor footprint, PT-BR inferior ao Whisper em ruído. Mantido
  como candidato para aparelhos fracos.
- **Só STT de plataforma**: suporte offline e qualidade PT-BR variam demais entre fabricantes para ser
  o baseline; vira fallback.

## Consequências
- (+) Fala nunca sai do aparelho sem política; funciona offline (INV-13/ADR-0010 honrados).
- (+) Motor plugável: a decisão não trava o desenho do 022.
- (−) Modelo on-device eleva o tamanho do app e restringe aparelhos antigos.
- (−) Exige o gate de avaliação PT-BR antes da POC — trabalho de medição, não só de código.

## Revisão
Reabrir se o gate de avaliação reprovar o baseline, se surgir STT on-device PT-BR claramente superior,
ou se a naturalidade do TTS virar requisito (traz Supertonic de volta à mesa).
