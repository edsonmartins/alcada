# Tarefas — 022 canal de voz

## Decisão prévia (gate) — ADR-0026 (proposto)
- [x] Decisão de STT/TTS registrada em ADR-0026 (Whisper small + fallback de plataforma; TTS nativo)
- [ ] Ratificar ADR-0026 e o limiar de WER de negócio
- [ ] Rodar o gate de avaliação PT-BR com ruído de carro antes da POC

## App (Flutter)
- [ ] Captura de áudio + persistência local ANTES da rede (INV-13)
- [ ] STT on-device atrás de uma porta (motor plugável)
- [ ] Interpretador de intenção: fala → intenção fechada + campos (INV-10); reformula se nada casa
- [ ] Resolução de referência vaga contra a fila (0/1/≥2 candidatos; 3ª tentativa → ADIAR)
- [ ] Confirmação de campos críticos em uma frase; comando só após "sim"
- [ ] Modos Ditar / Despachar
- [ ] Enfileira via a fila offline do 021 (não chama rede direto)

## Testes
- [ ] C1..C8 (intenção, confirmação, sem-confirmação, vaga×2, vaga→revisão, sem-intenção, offline, ditar)
- [ ] Métrica: taxa de correção após confirmação (instrumentação)
