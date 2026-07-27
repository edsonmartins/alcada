# Roteiro de gravação do corpus de WER (ADR-0026 §4)

Para **destravar o Whisper on-device** é preciso, antes, rodar o gate de avaliação
(ADR-0026 §4) num corpus **PT-BR com ruído de carro**. Este roteiro diz o que gravar,
como e como rotular. O harness que consome isso já existe (`lib/voz/aval/`, ver
[`gate-stt.md`](gate-stt.md)); falta só o áudio + as transcrições.

> ⚠️ **Segurança primeiro.** Não leia o roteiro dirigindo. Peça a um passageiro para
> conduzir a gravação, ou grave em segurança (parado, ou como carona). O objetivo é
> capturar o **ruído real**, não dirigir distraído.

## O que estamos medindo (e por quê)
- **WER geral** — o erro médio da transcrição. Se for alto, a interpretação sofre.
- **WER dos campos críticos** — nome, valor, prazo, nível. É onde o erro **custa a
  decisão** (ADR-0014 §2): confirmar "Alessandro N3" em vez de "Alexandre N2" é grave.
- (na mesma sessão, medir também **latência por frase**, **tamanho do app** e
  **bateria em ~30 min** — ADR-0026 §4 — mas isso sai do device, não do corpus.)

## Quantas falas
- **Mínimo: 30 falas** distintas (a planilha vem com 30 prontas).
- **Recomendado: as 30 × 3 condições = 90 clipes** (mesma frase em situações
  diferentes de ruído revela onde o motor quebra).
- Distribuição das 30: ~6 por intenção (resolver, repassar, adiar, registrar,
  consultar) + variação de **nomes próprios, valores e prazos** (é o que mais erra).

## Condições de ruído (gravar cada frase em ao menos 3)
1. **Cidade andando** — janela fechada, ar-condicionado ligado.
2. **Rodovia / velocidade** — ruído de vento e pneu mais alto.
3. **Parado no sinal / trânsito** — motor em marcha lenta, talvez rádio baixo.
4. (bônus) **Túnel / viaduto**, **janela aberta**, **rádio/música baixa**.

Fale no tom e distância que usaria de verdade (celular no suporte, não na boca).

## Formato do áudio
- **WAV, 16 kHz, mono** — é o que o app grava (`SttNuvem`), então casa com o pipeline.
- Se o gravador do aparelho só fizer `m4a`/`44.1 kHz`, tudo bem: converta depois para
  WAV 16 kHz mono (`ffmpeg -i in.m4a -ar 16000 -ac 1 out.wav`).
- Um arquivo por clipe.

## Nome dos arquivos
`f{NN}_{condicao}.wav` — ex.: `f03_rodovia.wav`, `f03_cidade.wav`, `f03_sinal.wav`.
O `NN` casa com a coluna `id` da planilha.

## Planilha (rótulos)
Use [`corpus_wer_template.csv`](corpus_wer_template.csv) — já vem com as 30 falas,
a **intenção**, a **referência** (o que dizer, palavra por palavra) e os **campos
críticos**. Para cada clipe gravado, preencha:
- `arquivo` — o nome do `.wav`;
- `condicao` — cidade | rodovia | sinal | tunel | janela | radio.

**Regra de ouro:** a `referencia` é o que foi **realmente dito**. Se você improvisar
uma frase diferente da planilha, **corrija a `referencia`** para bater com o áudio —
senão o WER mede errado.

## Campos críticos — convenção
Na coluna `campos_criticos`, separe por `|` os pedaços que a decisão depende:
- **nome** próprio do dono (repassar): `Alexandre`
- **nível**: `N2`
- **prazo**: `sexta`, `dia 15`, `segunda`
- **valor**: `12 mil`, `250 mil` (como foi falado)

O gate marca acerto se o campo aparece (normalizado) na transcrição.

## Como isso vira veredito
1. Para cada candidato — **aparelho**, **nuvem**, e depois **whisper-small** —
   transcrever todos os clipes.
2. Alinhar as hipóteses com as linhas da planilha e rodar `avaliar(...)` →
   `RelatorioAval` (WER médio + WER de campos).
3. Comparar com o **limiar de negócio** (`LimiarWer`). O número oficial é **seu**
   (ADR-0026 §4). Provisório: `WER ≤ 0,15` e `campos ≤ 0,05`.
4. Se o Whisper passar, adota-se on-device; se reprovar, ADR-0026 §Revisão manda
   reabrir a escolha (Vosk p/ aparelhos fracos, etc.).

## Definir o limiar (decisão do gestor)
Pergunta prática: **qual erro nos campos críticos é tolerável no trânsito?** Sugestão
para começar: **0 tolerância a erro de nome/valor** que passe sem o gestor perceber —
por isso a confirmação falada (ADR-0014 §2) existe. O limiar de `campos` deve refletir
quanto a confirmação consegue "pegar" antes de virar risco.
