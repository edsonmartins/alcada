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

## F1 — captura ciente de grupo (Alçada)
- [ ] `MensagemRecebida` += `grupo`, `grupoId`; webhook mapeia `group`/`sender`/`mentions`.
- [ ] `fonte` += `grupo`, `grupoId`; cadastro de fonte-grupo com finalidade (ADR-0011 §1).
- [ ] Bot visível: publicar/validar **aviso fixado** no grupo antes de capturar (ADR-0011 §2).
- [ ] Pré-filtro determinístico (menção / resposta a item / padrão de decisão);
      **log auditável da proporção processada** (ADR-0011 §3).
- [ ] Migration: colunas de grupo em `fonte`; tabela de conversa de grupo + `avaliado_ate_seq`.
- [ ] `docs/API.md` atualizado (novo formato do webhook).

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
