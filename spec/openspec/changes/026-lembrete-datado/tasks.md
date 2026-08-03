# Tarefas — 026 lembrete datado (RFC-0009)

## Motor e modelo (F2.1)
- [x] Migration `V35`: `pendencia.origem` (CAPTURA|ESCAPE|LEMBRETE) + `origem_pendencia_id`; vocabulário da trilha ganha `LEMBRETE_CRIADO`, `COMPROMISSO_AGENDADO`, `FALHA_COMPROMISSO` (anexo do ADR-0016 emendado)
- [x] Porta `Triagem.resolver(..., Lembrete, ...)` + record `Lembrete{quando, texto}`; a assinatura antiga vira wrapper
- [x] `TriagemService`: valida **antes** de fechar, cria a pendência-lembrete em `DORMINDO`, registra `LEMBRETE_CRIADO` na origem e agenda o `DESPERTAR` (C1–C3, C5)
- [x] Horizonte derivado da data no fuso do tenant (C6)
- [x] `POST /v1/pendencias/{id}/resolver` aceita `lembrete` (400 malformado, 422 inútil) (C9)
- [x] Testes `LembreteDatadoTest` (10) + `spec/docs/API.md`
- Nota (questão aberta 1 da RFC): o encolhimento conta eventos `CAPTADA`, e o lembrete não emite nenhum — a métrica já não infla, sem mudar `RadarJdbc`. Coberto por C7.

## Comando móvel e voz (F2.2)
- [x] `Comando.Campos.lembrete` ({quando, texto}) e `case RESOLVER` chamando `triagem.resolver(..., lembrete, ...)`; validação **antes** da transação, via `Lembrete.exigirUtil()` na porta (C12)
- [x] `InterpretadorVoz`: prompt com o "agora" no fuso do tenant; `lembreteQuando`/`lembreteTexto` no schema; data que não sobrevive à validação vira pergunta (C10/C11)
- [x] App (`alcada-mobile`): `LembreteComando` + `Despachador.resolver(lembrete:)`, interação `Perguntar`, fala do resultado "Te lembro quinta, 6, às 10h" (`dataFalada` em pt-BR, sem `intl`)
- [x] Offline (F2.2b): `Interpretador.dataDaFala` lê hoje/amanhã/dia da semana/"semana que vem" com hora opcional no relógio do aparelho; sem data legível o assistente pergunta (C12b)
- [x] Testes: backend `InterpretadorVozTest` (+4) e `ComandoRepasseExternoTest` (+2); mobile `test/lembrete_datado_test.dart` (7); `spec/docs/API.md`

## Calendário (F2.3 Google, F2.4 Outlook)
- [ ] Porta `Calendario` + `ContasCalendario` (OAuth por gestor, token cifrado, escopo mínimo)
- [ ] `EVENTO_CALENDARIO` no outbox após a janela; `evento_calendario_id` na pendência-lembrete (C13)
- [ ] Desfazer na janela descarta o evento; cancelar depois enfileira `CANCELAR_EVENTO_CALENDARIO` (C14)
- [ ] Retry e `FALHA_COMPROMISSO` (C15)
- [ ] Adaptador Outlook no mesmo contrato

## Web (F2.5)
- [ ] Conectar/revogar o calendário na sessão (C16)
- [ ] O lembrete visível na Entrada ao despertar (herda a tela; validar rótulo/ícone de origem)

## Questões abertas herdadas da RFC-0009
- [x] 1. Métricas — resolvida na F2.1: sem `CAPTADA`, o lembrete não entra no encolhimento
- [ ] 2. Sync bidirecional (mover/cancelar no calendário) — RFC própria
- [ ] 3. Lembrete sem pendência de origem ("me lembra de ligar pro Paulo") — é `REGISTRAR` datado; mesma máquina?
- [ ] 4. Recorrência — provavelmente não (ADR-0018)
- [ ] 5. Duração padrão do evento; convidar participantes (proposta: não convida)
- [ ] 6. Fuso do gestor viajando × fuso do tenant
