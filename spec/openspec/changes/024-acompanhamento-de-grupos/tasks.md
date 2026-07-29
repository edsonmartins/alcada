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
- [ ] Pré-filtro determinístico (menção / resposta a item / padrão de decisão);
      **log auditável da proporção processada** (ADR-0011 §3).
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
- [ ] Scheduler persistente (sem timer em memória): debounce + janela N + poll;
      marca `avaliado_ate_seq` avançada dentro da transação/outbox.
- [ ] Minimizador (ADR-0020 §3) da janela; re-hidratação local; teste de vazamento.
- [ ] Chamada ao gateway com `Sensibilidade` + `json_schema` estrito (falha se o
      provedor não suportar; nunca degrada — CLAUDE.md §7).
- [ ] Schema de saída (compromisso) + validação/normalização (data no fuso do tenant;
      tipo no conjunto fechado); `dependeDoGestor=false`/baixa confiança → descarta.
- [ ] Stub em dev/test (não bate no provedor); real só com o gateway ligado.

## F3 — superfície, cobrança, aprendizado
- [ ] Criar/fundir `Pendencia` a partir do compromisso (INV-10; ator `ASSISTENTE:` na trilha).
- [ ] Cobrança: fundir por assunto/thread + contador; limiar → ESCALADA + rótulo "Nx".
- [ ] Descarte realimenta o pré-filtro por grupo (011).
- [ ] Entrada mostra origem "grupo X" e o dono por 1º nome.

## Testes (cada cenário do spec.md)
- [ ] C1 reunião do Marcello → 1 compromisso estruturado (caso de aceite)
- [ ] C2 ruído não vai ao modelo · C3 não-depende-dele não entra
- [ ] C4 cobrança funde+escala · C5 menção fura debounce
- [ ] C6 bot invisível → sem captura · C7 minimizador sem vazamento
- [ ] C8 identidade mínima (1º nome / contato só quando é a ação)
- [ ] C9 idempotência · C10 isolamento tenant · C11 retenção ≤30d · C12 ator na trilha
