# Tasks — 024 acompanhamento de grupos

Desenho aguardando aprovação (CLAUDE.md §6, passo 3). Não implementar antes do "ok".

## Emenda de ADR
- [ ] Emenda ao **ADR-0011** (neste pacote): (a) captura seletiva de grupo por
      **padrão de decisão** (pré-filtro), mantendo "varredura completa proibida" +
      log de proporção; (b) **identificação mínima por finalidade** (1º nome padrão).
- [ ] Confirmar que não conflita com ADR-0017 (nada de métrica sobre membros) nem
      ADR-0020 (minimizador antes do modelo).

## F0 — contrato de grupo no Linktor (repo linktor, Go)
- [ ] Propagar no `message.received`: `group{id,name}`, `sender{id,name}`, `mentions[]`
      (o dado já existe no adaptador WhatsApp; falta o dispatcher propagar).
- [ ] Endpoint/evento sob demanda para participantes (`GET group/{id}` ou `group.info`).
- [ ] Garantir `ignore_groups=false` no canal do cliente; doc do envelope atualizada.

## F0 — contrato de grupo no Linktor (repo linktor, Go)
- [x] Propagar `group{id}` no `message.received` (envelope + dispatcher + produtor);
      `senderId` do indivíduo mantido; ausente em 1:1. Testes verdes. (branch feat pendente de push)
- [ ] `GET /channels/{id}/groups` (listar grupos do canal) — para a seleção (F1b).
- [ ] `mentions[]` no envelope (sinal "gestor foi marcado") — enhancement.

## F1 — captura ciente de grupo (Alçada)
- [x] `MensagemRecebida` += `grupo`, `grupoId`; webhook mapeia `data.group.id`,
      threadeia pelo grupo, `autor_ext` = indivíduo. (mentions: pendente com F0)
- [x] Migration `V28`: `evento_bruto.grupo` + índice `(org_id, thread_ref) where grupo`.
- [x] Teste: envelope com `group.id` → `evento_bruto.grupo` + thread pelo grupo.
- [x] Pré-filtro determinístico (`PreFiltroGrupo`): candidata se há item aberto do
      grupo, pergunta direcionada ("?"), ou casa com o léxico de decisão (radical, sem
      acento; configurável por `grupos.prefiltro.lexico`). Ruído puro é descartado antes
      do modelo (C2). **Log auditável da proporção** por fonte em `captura_proporcao`
      (V31): `janelas_vistas` vs `janelas_processadas`. (menção via `mentions` → com F0.)
- [ ] `docs/API.md` atualizado (novo formato do webhook com `group`).

## F1b — seleção de grupos (o gestor escolhe o que controlar)
- [x] Migration `V29` `grupo_acompanhado` (segredo é por CANAL; seleção por GRUPO —
      modelo revisado vs colunas em `fonte`). UNIQUE (fonte_id, grupo_id).
- [x] Descoberta no webhook: grupo visto → upsert de metadados (id/nome/último visto),
      sem conteúdo; só ingere se `ativa` (opt-in). Grupo não selecionado → descartado.
- [x] `GET /v1/grupos` (grupos vistos, com `ativa`) + `PUT /v1/grupos/{grupoId}`
      (ativar/desativar com finalidade — ADR-0011 §1). `docs/API.md` atualizado.
- [x] Testes: C13 (não selecionado descartado mas descoberto) + GruposResourceTest (GET/PUT/404).
- [ ] Bot visível: publicar **aviso fixado** no grupo ao ativar (ADR-0011 §2) — via Canal/outbox.
- [ ] `GET /channels/{id}/groups` no Linktor + `GET /v1/grupos` também popular de lá
      (hoje lista só o que o bot já viu por mensagem).
- [ ] Tela de seleção de grupos (web/mobile).

## F2 — extrator por janela
- [x] `ExtratorGrupo`: chamada ao gateway (`Sensibilidade.INTERNA` + `json_schema`
      estrito de compromisso), instrução "depende dele?" no texto, parse/validação
      (tipo no conjunto fechado), reprocesso 1× e indisponibilidade → vazio (INV-10).
      `Compromisso` (dependeDoGestor, tipo, assunto, quemPede, quando, ação, feito, conf).
- [x] Teste com gateway fake no caso C1 (reunião do Marcello) + não-depende + indisponível.
- [x] Scheduler persistente (sem timer em memória): `WorkerGrupos` @Scheduled(30s) →
      reserva grupos assentados (`ultimo_visto` além do debounce) com conteúdo novo
      (`ultimo_visto > avaliado_em`) via `FOR UPDATE SKIP LOCKED`, marca `avaliado_em`
      na mesma transação (V30). Reserva e processamento em txs separadas (o modelo não
      segura o lock do lote). Testes: assentado→avaliado; ainda quente→ignorado.
- [x] `ProcessadorGrupo`: monta a janela do grupo (evento_bruto, remetente por linha),
      minimiza (ADR-0020 §3, re-hidrata), chama `ExtratorGrupo`, e se `dependeDoGestor`
      → cria/funde pendência (ator `ASSISTENTE:` na trilha); cobrança esquenta+funde (não duplica).
      Testes: janela→Entrada, não-depende→nada, 2ª janela→cobrança (temperatura++).
- [x] Ingestao de grupo NÃO agenda PROCESSAR_CAPTURA por mensagem: a unidade é a janela,
      que o `WorkerGrupos` varre por debounce. Só 1:1 segue por PROCESSAR_CAPTURA.

## F3 — superfície, cobrança, aprendizado
- [x] Criar/fundir `Pendencia` a partir do compromisso (INV-10; ator `ASSISTENTE:` na trilha).
      (feito no `ProcessadorGrupo`, F2.)
- [x] Cobrança: funde na pendência do grupo, grava `cobranca` (contador + desfundir),
      `temperatura++`; ao cruzar `grupos.cobranca-escala` (default 2) → evento `ESCALADA`
      na trilha (uma vez). Read-model expõe `cobrancas` para o rótulo "Nx".
- [ ] Descarte realimenta o pré-filtro por grupo (011).
- [x] Entrada mostra origem "grupo X" (`origemGrupo`, join `grupo_acompanhado.nome`) e o
      dono por 1º nome (`quemEspera` já re-hidratado). `GET /v1/pendencias` estendido; API.md.
      UI: web (EntradaPage, selo grape + "já te cobraram Nx") e mobile (chips na fila).

## Testes (cada cenário do spec.md)
- [x] C1 reunião do Marcello → 1 compromisso estruturado (ExtratorGrupoTest + ProcessadorGrupoTest)
- [x] C2 ruído não vai ao modelo (PreFiltroGrupoTest + ProcessadorGrupoTest: 0 chamadas) · [x] C3 não-depende-dele não entra
- [x] C4 cobrança funde+escala (ProcessadorGrupoTest) · [ ] C5 menção fura debounce (depende de mentions)
- [ ] C6 bot invisível → sem captura · [x] C7 minimizador sem vazamento (GruposInvariantesTest)
- [x] C8 identidade mínima — 1º nome (GruposInvariantesTest; `ProcessadorGrupo.primeiroNome`)
- [x] C9 idempotência (ProcessadorGrupoTest: reprocesso funde) · [x] C10 isolamento tenant · [x] C11 retenção ≤30d · [x] C12 ator na trilha
