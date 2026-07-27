# Integração do Whisper on-device (ADR-0026 §2)

**Fazer só depois de o gate de WER passar** (ADR-0026 §4 — ver [`gate-stt.md`](gate-stt.md)
e [`roteiro-corpus-wer.md`](roteiro-corpus-wer.md)). Este guia deixa a integração
pronta para preencher num Mac/dispositivo; o esqueleto já está no app
(`lib/voz/stt_whisper.dart`), atrás do seam `transcreverArquivo(wav)`.

## O seam
`SttWhisper.transcreverArquivo(caminhoWav)` recebe um WAV **16 kHz mono** e devolve
o texto — **localmente**, sem sair do aparelho (INV-13/ADR-0010). É o mesmo ponto
que:
- o **gate** usa para o candidato `whisper-small` (transcrever os clipes do corpus);
- o **hold-to-talk** usa (gravar → transcrever no device), como a nuvem faz hoje,
  mas sem enviar áudio.

Enquanto não instalado, `disponivel()` é `false` e `transcreverArquivo` lança
`WhisperNaoInstalado` — o roteador de STT simplesmente não escolhe o Whisper e cai
no STT do aparelho (fallback do ADR-0026).

## Passos
1. **Plugin** (whisper.cpp, licença MIT — distribuível Cloud e Soberano). Avaliar o
   estado de manutenção na hora; candidatos: `whisper_ggml` ou `whisper_flutter_new`.
   Adicionar como dependência (é a parte que incha o build e exige config nativa).
2. **Modelo** ggml quantizado — `small` q5 (~180–250 MB). **Baixar no 1º uso** para o
   diretório de suporte do app (`path_provider`), nunca no bundle. Aparelhos fracos:
   `base`/`tiny`, ou Vosk (ADR-0026 §Alternativas).
3. **Preencher** `SttWhisper.transcreverArquivo`: apontar o modelo baixado, chamar a
   transcrição do plugin (idioma `pt`), devolver o texto. `disponivel()` = binding
   presente **e** modelo baixado.
4. **Gravação** reutiliza o `RecordConfig` do `SttNuvem` (WAV 16 kHz mono) — extrair
   um gravador comum se ajudar. Hold-to-talk: gravar → `transcreverArquivo`.
5. **Roteamento** (`TelaVoz._gravarInicio`): a ordem passa a ser
   `SKU Cloud + online → nuvem`; senão `SttWhisper.disponivel() → whisper`; senão
   **aparelho**. Assim o Soberano usa Whisper local (nunca nuvem), e o Cloud usa
   nuvem quando online.
6. **Permissões/build**: microfone (já) + espaço para o modelo. iOS: linkar
   `libwhisper`, modelo no *app support* (não no bundle). Android: NDK + `abiFilters`,
   `minSdk` compatível. Medir **bateria/thermal** num trajeto (ADR-0026 §4).

## Rodar o gate com o Whisper
```
final amostras = amostrasDeCsv(csvDoRoteiro);
final hip = [ for (final a in amostras) await SttWhisper().transcreverArquivo('corpus/${a.id}_cidade.wav') ];
final r = avaliar('whisper-small', amostras, hip);
if (r.passou(const LimiarWer())) { /* adota on-device */ }
```

## Ordem (não pular)
Corpus gravado → limiar definido → gate roda com os 3 candidatos → **se** o Whisper
passar, adota-se; **senão**, ADR-0026 §Revisão manda reabrir a escolha.
