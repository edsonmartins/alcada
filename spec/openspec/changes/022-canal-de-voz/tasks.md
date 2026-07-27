# Tarefas — 022 canal de voz

## Decisão prévia (gate) — ADR-0026 (aceito)
- [x] Decisão de STT/TTS registrada e **ratificada** (ADR-0026 aceito): Whisper small + fallback; TTS nativo
- [~] Definir o limiar de WER de negócio (número do piloto) — `LimiarWer` provisório (wer≤0,15 / campos≤0,05) no app; número oficial é decisão do gestor
- [~] Rodar o gate de avaliação PT-BR com ruído de carro antes da POC — harness pronto e testado (`lib/voz/aval/`, ver `gate-stt.md`); falta o corpus real (áudio+transcrição) para rodar de fato

## App (Flutter — repo alcada-mobile)
- [x] STT on-device atrás de uma porta (`Stt`/`SttManual`; motor Whisper pluga depois — ADR-0026)
- [x] Interpretador de intenção: fala → intenção fechada + pistas (INV-10); reformula se nada casa
- [x] Resolução de referência vaga contra a fila (0/1/≥2 candidatos; 3ª tentativa → revisão)
- [x] Confirmação de campos críticos em uma frase; comando só após "sim"
- [x] Modos Ditar (registrar/consultar) / Despachar (resolver/adiar)
- [x] Enfileira via a fila offline do 021 (não chama rede direto)
- [x] Superfície (TelaVoz) rodando em device real (Galaxy Tab SM X115)
- [x] STT real on-device da plataforma (Android SpeechRecognizer pt-BR) — fallback ADR-0026; testado no tablet
- [x] STT na NUVEM via gateway (`POST /v1/voz/transcrever` → OpenRouter whisper-large-v3-turbo) — verificado ao vivo (HTTP 200); só SKU Cloud
- [x] Interpretação por LLM com memória de conversa (`POST /v1/voz/interpretar`, `InterpretadorVoz`): fala livre → UMA intenção do conjunto fechado (INV-10) + contexto dos turnos (follow-ups); app envia turnos+fila e cai no matcher local offline (INV-13). Verificado ao vivo
- [x] App: gravar áudio (plugin `record`) + enviar ao /v1/voz/transcrever; usar nuvem online, device offline (híbrido) — `ApiCliente.online()` sonda o health (timeout curto); o "Segurar para falar" roteia sozinho: online → nuvem (record→transcrever), offline → STT do aparelho (INV-13). Testado
- [~] Whisper `small` on-device (baseline ADR-0026) — SÓ após o gate passar (ADR-0026 §4). Esqueleto `SttWhisper` (seam `transcreverArquivo`, `disponivel()=false`, não adotado) + guia `whisper-integracao.md` prontos; falta corpus + limiar oficial, aí instala whisper.cpp e preenche o seam
- [x] Repassar por voz precisa de diretório de pessoas (nome → pessoa_id) — porta `identidade.Pessoas` + `PessoasJdbc` (match sem acento por prefixo); `InterpretadorVoz` resolve donoNome→pessoa_id (1 match confirma / ≥2 candidatos p/ escolha / 0 avisa); app com fluxo `EscolherPessoa`. Verificado ao vivo (3 caminhos)
- [x] Memória durável de apelidos (fatia B) — tabela `apelido_pessoa` (V22, por org+gestor); o próprio gestor nunca é candidato; nome não reconhecido oferece a equipe e, ao confirmar o repasse, `Pessoas.aprender` grava o termo→pessoa (ignora redundantes); apelido tem prioridade na resolução. Verificado ao vivo (B1 exclusão do gestor; B2 aprende/resolve "Xandão")
- [x] Preferências do gestor (fatia C1) — tabela `preferencia_gestor` (V23); nível de repasse habitual aprendido do uso (`Preferencias`); REPASSAR sem nível dito usa a preferência (senão N2); nível dito é normalizado (3→N3). O prompt não deixa o LLM inventar nível. Verificado ao vivo
- [x] "O que eu decidi ontem/esta semana" (fatia C2) — template `DECISOES_RECENTES` lê a trilha filtrada por ator (HUMANO:gestor) + tipo de decisão + período (ONTEM/HOJE/SEMANA/TRIMESTRE, fuso do tenant); `Consulta.consultar` ganha sobrecarga com gestor; `/v1/consulta` lê X-Pessoa-Id. Verificado ao vivo

## Testes
- [x] C1/C2/C3/C4/C5/C6/C8 no cérebro da voz (7 testes; 16 no total, analyze limpo)
- [ ] C7 (áudio offline persistido) — depende do STT real
- [x] Métrica: taxa de correção após confirmação — tabela voz_confirmacao (V27, por org/ADR-0017), POST /v1/voz/feedback + GET /v1/voz/taxa-correcao; app instrumenta confirmar (CONFIRMADO) e correção/cancelar (CORRIGIDO). Verificado ao vivo (taxa 0,25)
