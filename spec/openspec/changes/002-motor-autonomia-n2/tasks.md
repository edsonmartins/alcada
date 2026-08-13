# Tasks — 002 motor de autonomia N2

## Domínio
- [x] Migrations de `delegacao`, `classe_decisao`, `ausencia` (reusa `job`/`outbox`) — V7, todas com org_id
- [x] Máquina de estados da delegação com transições válidas explícitas (CHECK + guardas no motor)
- [x] Validação de elegibilidade por classe (nível máximo; valor-limite fica para classes de negócio finas)

## Motor
- [x] Scheduler persistente com claim por lock e retry exponencial (reuso do bootstrap)
- [x] Transição `AGUARDANDO_JANELA` no vencimento do prazo (só com PROPOSTA_REGISTRADA)
- [x] Execução ao fim da janela, com publicação no outbox (efeito só após a janela — INV-14)
- [x] Escalonamento por silêncio de ambos (sem proposta → ESCALADA, não executa)
- [x] Modo ausência: conversão automática N2 → N3

## API
- [x] `POST /v1/pendencias/{id}/delegar`
- [x] `POST /v1/pendencias/{id}/intervir`
- [x] `POST /v1/pendencias/{id}/desfazer` (valida janela → 409 janela.expirada)
- [x] `POST /v1/delegacoes/{id}/propor` + `GET /v1/delegacoes`

## Notificação
- [x] Lembrete ao executor a 50% e ao gestor a 90% do prazo — jobs persistentes, outbox idempotente e `MotorAutonomiaTest.lembretes_50_e_90_notificam_executor_e_gestor`
- [x] Aviso de execução por ausência / escalonamento / interrupção (efeitos no outbox)

## Trilha
- [x] `EXECUTADA_POR_AUSENCIA` com prazo, proposta e ausência de intervenções (carga completa)
- [x] `PROPOSTA_REGISTRADA`, `JANELA_INICIADA`, `DESFEITA_NA_JANELA`, `INTERROMPIDA`, `ESCALADA`, `CONVERTIDA_POR_AUSENCIA`

## Testes
- [x] Reinício da app no meio da janela não duplica nem perde execução
- [~] Corrida entre intervenção e vencimento — coberta pelas guardas por status; teste de corrida real fica com carga
- [x] Classe inelegível recusa N2 com `422`
- [x] Fuso e horário comercial na virada do dia — calendário por tenant e lembretes úteis entregues no P032

## Decisões (aprovadas no plano)
- Execução por ausência exige `PROPOSTA_REGISTRADA` (RFC-0002); cenário 1 propõe, cenário 5 não.
- `classe_decisao` por classe ampla + defaults (janela PT4H, escalonamento PT24H).
- Efeito externo só na VIRADA; `desfazer` após publicação → 409.
- Superfície do executor (005), mineração/`promover` (008), lembretes 50/90% fora de escopo.
