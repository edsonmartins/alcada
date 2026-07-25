# Design — 003 triagem web por teclado

Referências: `adr/ADR-0002-quatro-saidas-e-adiamento.md`, `adr/ADR-0018-anti-jardinagem.md`,
`prototipo/alcada-sistema.html`.

## Módulos
`triagem` (backend: transições das saídas + adiar + `/hoje`) e o app **web** (SPA). O `repassar`
delega para a porta do motor de autonomia (002); a triagem não reimplementa delegação.

## Estados (reuso da máquina do PRODUTO §5)
```
ENTRADA ─ resolver ─► FECHADA           (trilha RESOLVIDA · notifica solicitante)
        ─ repassar ─► DELEGADA          (motor 002 · trilha REPASSADA)
        ─ reservar ─► AGENDADA          (agendado_para · trilha RESERVADA · conta como dependente)
        ─ repousar ─► DORMINDO ─► ENTRADA (volta_em/cobrança · trilha REPOUSADA/DESPERTADA)
        ─ adiar    ─► ENTRADA           (volta_em + o_que_falta · adiado_count++ · trilha ADIADA)
```

## Tabelas
```sql
adiamento(id, org_id, pendencia_id, volta_em, o_que_falta, ocorrencia, criado_em)
-- pendencia ganha: agendado_para timestamptz (reservar), volta_em timestamptz (repousar/adiar)
```
`ocorrencia` é o contador de adormecimento (RFC-0002): a chave do job de despertar é
`(pendencia_id, transicao, ocorrencia)`.

## API
```
POST /v1/pendencias/{id}/resolver   { nota? }
POST /v1/pendencias/{id}/reservar   { agendado_para, gerar_dossie:false }
POST /v1/pendencias/{id}/repousar   { volta_em }
POST /v1/pendencias/{id}/adiar      { volta_em, o_que_falta: NADA|INSUMO|TERCEIRO }
GET  /v1/hoje                       -> no máximo 3 itens + justificativa por item
```
`repassar` já existe (002). Efeitos externos (notificação de fechamento) via outbox; despertar de
`DORMINDO`/adiamento via scheduler persistente, idempotente por ocorrência.

## Resposta diferenciada do adiar (ADR-0002)
- `NADA` → resposta oferece **bloco de decisão** ("não está bloqueado, está evitado")
- `TERCEIRO` → oferece **repassar** para quem tem a bola
- `INSUMO` → passa a **cobrar o insumo**, não a lembrar o item

## Web (ADR-0023)
React 19 + TypeScript + Mantine v9 + Vite; TanStack Query (cache + **mutação otimista** = janela de
desfazer, INV-14), TanStack Router, Zustand (estado de UI), React Hook Form + Zod (os únicos
formulários: `repassar` e `adiar`). SPA pura, autenticada por OIDC. Referência visual do protótipo.

### Superfícies
- **Entrada** — lista com **cursor de teclado**; cada item mostra próxima ação (nunca contemplativa).
- **Hoje** — no máximo 3, com a justificativa.
- **Drawer** — detalhe + formulários de `repassar`/`adiar`.
- **Paleta ⌘K** — comandos.
- **Lote** — seleção múltipla para `resolver`/`repousar`.

### Atalhos (do protótipo)
`j`/`k` (ou ↑/↓) navega · `Enter` abre · `1–4` = Resolver/Repassar/Reservar/Repousar · `a` adiar ·
`/` busca · `⌘K` paleta · `Esc` fecha. Adiar é botão **secundário**, nunca portão (ADR-0002).

## Anti-jardinagem (ADR-0018) — invariantes de UI
- **Nenhum** componente de arrastar (drag-and-drop) existe no código.
- Sem campo livre obrigatório; sem taxonomia editável pelo usuário.
- **Sessão improdutiva nomeada**: tempo de uso sem transição de estado dispara aviso.
- Lote incentivado; edição item-a-item de metadado, não.

## Riscos
Mutação otimista + janela de desfazer precisa reconciliar com o servidor sem piscar a lista.
Contador de adiamentos é diagnóstico (INV-07) — exibido com ação, nunca como placar.
