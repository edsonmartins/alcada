# Tarefas — 022 canal de voz

## Decisão prévia (gate) — ADR-0026 (aceito)
- [x] Decisão de STT/TTS registrada e **ratificada** (ADR-0026 aceito): Whisper small + fallback; TTS nativo
- [ ] Definir o limiar de WER de negócio (número do piloto)
- [ ] Rodar o gate de avaliação PT-BR com ruído de carro antes da POC

## App (Flutter — repo alcada-mobile)
- [x] STT on-device atrás de uma porta (`Stt`/`SttManual`; motor Whisper pluga depois — ADR-0026)
- [x] Interpretador de intenção: fala → intenção fechada + pistas (INV-10); reformula se nada casa
- [x] Resolução de referência vaga contra a fila (0/1/≥2 candidatos; 3ª tentativa → revisão)
- [x] Confirmação de campos críticos em uma frase; comando só após "sim"
- [x] Modos Ditar (registrar/consultar) / Despachar (resolver/adiar)
- [x] Enfileira via a fila offline do 021 (não chama rede direto)
- [x] Superfície (TelaVoz) rodando em device real (Galaxy Tab SM X115)
- [ ] Captura de áudio + STT real Whisper on-device (hoje: stand-in por texto)
- [ ] Repassar por voz precisa de diretório de pessoas (nome → pessoa_id)

## Testes
- [x] C1/C2/C3/C4/C5/C6/C8 no cérebro da voz (7 testes; 16 no total, analyze limpo)
- [ ] C7 (áudio offline persistido) — depende do STT real
- [ ] Métrica: taxa de correção após confirmação (instrumentação)
