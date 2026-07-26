# 021 — App móvel: base offline-first e sincronização de comandos

**Fase:** F5 · **Implementa:** ADR-0015 · RFC-0005 · **Honra:** INV-13, INV-14, INV-15, INV-10
**Depende de:** 001 (captura), 002 (autonomia), 003 (triagem), 020 (consulta)

## Problema
O gestor passa 40–90 min/dia em trânsito e resolve pendências por telefone. O
método da Alçada (uma pergunta, quatro saídas, sem comparação) sobrevive sem
tela. Falta a base: um app que **capture e despache offline**, sem nunca perder
uma fala (INV-13), sincronizando de forma idempotente com o backend.

Este pacote entrega a **fundação** — app Flutter + contrato de sincronização.
Voz (022) e modo trajeto (023) entram sobre esta base.

## Proposta
1. **App Flutter offline-first** (ADR-0015): fila local persistida de comandos,
   sync idempotente, sessão reaproveitando o mesmo contexto de tenant do web.
2. **Endpoint de sincronização** `POST /v1/comandos` no backend: recebe um lote
   de comandos já interpretados (intenção + campos), executa cada um de forma
   **determinística** mapeando para as ações existentes (resolver/repassar/
   reservar/repousar/adiar/registrar/consultar), **idempotente** por
   `(org_id, comando_id)`.
3. **Sem reatividade** (CLAUDE.md §8): o app **não** emite notificação de "novo
   item na entrada". Ele é superfície de despacho, não de alerta.

## Não-objetivos
- STT / interpretação de voz (pacote 022) — aqui os comandos chegam já
  estruturados (o app web/testes podem produzi-los; a voz é a fonte real depois).
- Modo trajeto / detecção de movimento / recusa por classe (pacote 023).
- Redis: idempotência é PostgreSQL (ADR-0023 sobrepõe a menção a Redis do ADR-0015).
