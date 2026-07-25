# OpenSpec — pacotes de mudança

Unidade de execução para Claude Code. Cada pacote: `proposal.md`, `design.md`, `tasks.md`, `spec.md`
(cenários WHEN/THEN).

## Índice

| # | Pacote | Fase | Implementa | Estado |
|---|---|---|---|---|
| 001 | `captura-multicanal` | F1 | ADR-0005/06/07/11/21 · RFC-0001 | **escrito** |
| 002 | `motor-autonomia-n2` | F1 | ADR-0003/04/16 · RFC-0002 | **escrito** |
| 003 | `triagem-web-teclado` | F1 | ADR-0002/18 | **escrito** |
| 018 | `gateway-de-modelos` | F1 | ADR-0020 · RFC-0007 | **implementado** (OpenRouter/DeepInfra real) |
| 019 | `linktor-real` | F2+ | ADR-0021 · ADR-0025 | **implementado** |
| 004 | `trilha-imutavel` | F1 | ADR-0016 | **escrito** |
| 005 | `superficie-executor` | F2 | ADR-0013 · RFC-0002 | **escrito** |
| 006 | `fechamento-canal-origem` | F2 | ADR-0013 | **escrito** |
| 007 | `portal-externo` | F2 | ADR-0013 · RFC-0006 | **escrito** |
| 008 | `mineracao-alcada` | F3 | ADR-0003 · RFC-0003 | a escrever |
| 009 | `radar-e-metricas` | F3 | ADR-0017 | a escrever |
| 010 | `revisao-semanal` | F3 | RFC-0004 | a escrever |
| 011 | `bloco-de-decisao` | F4 | ADR-0019 · RFC-0004 | a escrever |
| 012 | `assistente-dossie` | F4 | ADR-0009/19 · RFC-0004 | a escrever |
| 013 | `assistente-redacao` | F4 | ADR-0019 · RFC-0004 | a escrever |
| 014 | `esteira-e-checklist` | F4 | ADR-0012 · RFC-0006 | a escrever |
| 015 | `app-mobile-base` | F5 | ADR-0015 · RFC-0005 | a escrever |
| 016 | `canal-de-voz` | F5 | ADR-0014 · RFC-0005 | a escrever |
| 017 | `modo-trajeto` | F5 | ADR-0014 · RFC-0005 | a escrever |

## Regra
Nenhum pacote pode contradizer `CONSTITUTION.md`. Se precisar, primeiro se escreve o ADR de revogação.
