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

## Calendário — porta e entrega (F2.3a)
- [x] Porta `Calendario` (+ `CriarEvento`) em `notificacao`, com `CalendarioStub` fora de prod — nenhum domínio conhece Google/Microsoft (ADR-0021)
- [x] `Lembrete.comCalendario` (porta, endpoint, comando) e `V36` com `pendencia.evento_calendario_id`
- [x] `Outbox.publicarApos` (janela antes do efeito, `alcada.calendario.janela` default PT5M) e `Outbox.descartarPendente` (C13/C14)
- [x] `DespachanteCanal` case `EVENTO_CALENDARIO` → cria o evento, grava o id e registra `COMPROMISSO_AGENDADO`; sem conta → `FALHA_COMPROMISSO` sem retentativa eterna (C15)
- [x] Testes `CompromissoCalendarioTest` (6) + `spec/docs/API.md`
- Nota (divergência consciente da RFC): a RFC dizia "após a janela" pensando na janela do motor (horas). Para o compromisso isso deixaria a agenda vazia por horas — a janela virou config curta (5 min), o suficiente para o desfazer.

## Calendário — provedor real (F2.3b Google, F2.4 Outlook)
- [ ] `ContasCalendario` + `conta_calendario` (OAuth por gestor, token cifrado, escopo mínimo, revogação) (C15b)
- [ ] `GoogleCalendarHttp` (@prod): criar evento, refresh de token, erros → `CalendarioIndisponivel`/`SemConta`
- [ ] Cancelar: `Calendario.cancelarEvento` + `CANCELAR_EVENTO_CALENDARIO` no outbox quando o lembrete é cancelado depois da janela
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
