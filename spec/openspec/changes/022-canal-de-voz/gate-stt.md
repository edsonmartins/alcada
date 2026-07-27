# Gate de avaliação de STT (ADR-0026 §4)

O ADR-0026 exige, **antes de adotar qualquer motor de STT** (incl. Whisper on-device),
medir num corpus **PT-BR com ruído de carro**. Só depois de passar no limiar de
negócio o motor entra. Este documento descreve o gate implementado.

## O que está pronto (app `alcada-mobile`)
- `lib/voz/aval/metricas_stt.dart` — métricas puras: `wer()` (Levenshtein em palavras,
  normalizando caixa/acento/pontuação) e `werCampos()` (erro só nos campos críticos:
  nome, valor, prazo — onde o erro custa a confirmação, ADR-0014 §2).
- `lib/voz/aval/gate_stt.dart` — `avaliar(candidato, amostras, hipoteses)` → `RelatorioAval`
  (WER médio + WER de campos médio); `RelatorioAval.passou(LimiarWer)`.
- Testado em `test/gate_stt_test.dart`.

## Corpus (a fornecer pelo piloto)
Uma lista de amostras; para cada uma:
- **áudio** gravado no aparelho, em condição real (carro andando, janela, rádio baixo);
- **referência**: o que foi dito, transcrito à mão;
- **camposCriticos**: os pedaços que a decisão depende (ex.: `["Alexandre","N2","sexta"]`).

Sugestão de tamanho mínimo: ~30–50 falas cobrindo resolver/repassar/adiar/consultar,
com nomes próprios variados. Sem áudio real, o gate não roda de verdade — só a lógica.

## Como rodar (quando houver corpus)
Para cada candidato (`aparelho`, `nuvem`, futuro `whisper-small`): transcrever cada
áudio → juntar as hipóteses na ordem das amostras → `avaliar(...)` → comparar com o
limiar. Medir também **latência/frase**, **tamanho do app** e **bateria em ~30 min**
(ADR-0026 §4) — estes ficam fora das métricas puras (dependem de device).

## Limiar de negócio — PENDENTE (decisão do gestor)
`LimiarWer` traz um **provisório** (`wer ≤ 0,15`, `werCampos ≤ 0,05`). O número oficial
é do piloto (ADR-0026 §4: "a definir com o piloto"). Ajustar em `LimiarWer` quando
definido.

## Próximo passo (só após o gate passar)
Plugar o **whisper.cpp `small`** atrás da porta `Stt` (motor on-device, ~466 MB
quantizável, download no 1º uso). Se o gate reprovar, ADR-0026 §Revisão manda reabrir
a escolha (Vosk p/ aparelhos fracos, etc.).
