# Tasks — 003 triagem web por teclado

## Backend — transições das saídas
- [x] Migration: `adiamento` + colunas `agendado_para`/`volta_em`/`ocorrencia` em `pendencia`; guarda de org_id (V8)
- [x] `POST /v1/pendencias/{id}/resolver` → FECHADA + trilha RESOLVIDA + outbox `item.fechado`
- [x] `POST /v1/pendencias/{id}/reservar` → AGENDADA + trilha RESERVADA
- [x] `POST /v1/pendencias/{id}/repousar` → DORMINDO + trilha REPOUSADA + job de despertar
- [x] `POST /v1/pendencias/{id}/adiar` → validação (`volta_em` 400, `o_que_falta` 422) + adiado_count + trilha ADIADA
- [x] Resposta diferenciada do adiar (NADA/TERCEIRO/INSUMO)
- [x] Despertar de DORMINDO/adiamento via scheduler, idempotente por ocorrência (trilha DESPERTADA)
- [x] `GET /v1/hoje` — no máximo 3 + justificativa (função de ordenação do PRODUTO §6)

## Web — bootstrap
- [x] Projeto Vite + React 19 + TypeScript + Mantine v9 (ADR-0023); SPA pura (OIDC: contexto por header nesta fase)
- [x] TanStack Query + Router; Zustand; React Hook Form + Zod
- [x] Cliente de API tipado (contra docs/API.md) + tenant/header e tratamento de `problem+json`

## Web — triagem
- [x] Tela **Entrada** com cursor de teclado (`j`/`k`/`Enter`) e próxima ação por item
- [x] Atalhos `1–4` (saídas) e `a` (adiar, secundário); `Espaço` lote; `Esc`
- [~] `/` busca e `⌘K` paleta — atalhos previstos; a superfície de busca/comandos entra num incremento
- [x] Drawer de detalhe + formulários `repassar` e `adiar` (RHF + Zod)
- [x] Tela **Hoje** (≤3, com justificativa)
- [x] Ação em lote (seleção múltipla → resolver/repousar)
- [x] Janela de desfazer por **mutação otimista** (INV-14)

## Anti-jardinagem (ADR-0018)
- [x] Teste que falha se houver qualquer componente de arrastar (drag-and-drop) + sem dep de dnd
- [x] Sem campo livre obrigatório / taxonomia editável
- [x] Nomear sessão improdutiva (uso sem transição de estado)

## Testes
- [x] Backend: um teste por cenário de transição/validação (resolver/reservar/repousar/adiar/hoje/despertar)
- [x] Web: operação por teclado (1–4/a/j/k), ausência de drag, lote, desfazer otimista (Vitest, 8 testes)

---
**Estado:** pacote 003 **completo** — backend (8 migrations, 58 testes JVM, nativo ~71 MB RSS) +
web (React 19 + Mantine 9 + Vite, 8 testes Vitest, build de produção OK).
Incrementos previstos: busca `/` + paleta `⌘K`; login OIDC real no lugar do contexto por header.
