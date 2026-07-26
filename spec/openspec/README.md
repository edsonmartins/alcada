# OpenSpec — pacotes de mudança

Unidade de execução para Claude Code. Cada pacote: `proposal.md`, `design.md`, `tasks.md`, `spec.md`
(cenários WHEN/THEN).

## Índice

A numeração foi renumerada durante a execução; esta tabela reflete os
diretórios reais em `changes/` e o estado real do código.

| # | Pacote | Fase | Implementa | Estado |
|---|---|---|---|---|
| 001 | `captura-multicanal` | F1 | ADR-0005/06/07/11/21 · RFC-0001 | **implementado** |
| 002 | `motor-autonomia-n2` | F1 | ADR-0003/04/16 · RFC-0002 | **implementado** (+ lembretes 50/90%, fuso por tenant) |
| 003 | `triagem-web-teclado` | F1 | ADR-0002/18 | **implementado** (+ descarte de 1 toque) |
| 004 | `trilha-imutavel` | F1 | ADR-0016 | **implementado** (+ arquivamento frio) |
| 005 | `superficie-executor` | F2 | ADR-0013 · RFC-0002 | **implementado** |
| 006 | `fechamento-canal-origem` | F2 | ADR-0013 | **implementado** |
| 007 | `portal-externo` | F2 | ADR-0013 · RFC-0006 | **implementado** |
| 009 | `radar-e-revisao` | F3 | ADR-0017 · RFC-0004 | **implementado** (+ saúde do gateway, condução §4) |
| 010 | `mineracao-regras-autonomia` | F3 | ADR-0003 · RFC-0003 | **implementado** |
| 011 | `laco-de-aprendizado` | F3 | ADR-0003/19 · RFC-0003 | **implementado** |
| 012 | `esteira-e-checklist` | F4 | ADR-0012 · RFC-0006 | **implementado** |
| 013 | `bloco-de-decisao` | F4 | ADR-0019 · RFC-0004 | **implementado** |
| 014 | `dossie-indice-hibrido` | F4 | ADR-0009/19 · RFC-0004 | **implementado** (+ correção de premissa §1) |
| 015 | `portal-de-instancia` | F4 | ADR-0013 · RFC-0006 | **implementado** |
| 018 | `gateway-de-modelos` | F1 | ADR-0020 · RFC-0007 | **implementado** (OpenRouter/DeepInfra real) |
| 019 | `linktor-real` | F2+ | ADR-0021 · ADR-0025 | **implementado** |
| 020 | `consulta-linguagem-natural` | F4 | RFC-0004 §3 | **implementado** |
| — | `app-mobile-base` | F5 | ADR-0015 · RFC-0005 | a escrever |
| — | `canal-de-voz` | F5 | ADR-0014 · RFC-0005 | a escrever |
| — | `modo-trajeto` | F5 | ADR-0014 · RFC-0005 | a escrever |

## Regra
Nenhum pacote pode contradizer `CONSTITUTION.md`. Se precisar, primeiro se escreve o ADR de revogação.
