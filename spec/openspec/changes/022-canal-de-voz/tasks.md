# Tarefas — 022 canal de voz

## Decisão prévia (gate)
- [ ] Escolher motor de STT on-device PT-BR (qualidade com ruído de carro, tamanho, licença) — RFC-0005/DECISOES-ABERTAS
- [ ] Avaliar TTS (Supertonic e alternativas) para as frases de confirmação

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
