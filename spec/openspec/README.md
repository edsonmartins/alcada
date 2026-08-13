# OpenSpec — pacotes de mudança

Unidade de execução para Claude Code. Cada pacote: `proposal.md`, `design.md`, `tasks.md`, `spec.md`
(cenários WHEN/THEN).

## Índice

A numeração foi renumerada durante a execução; esta tabela reflete os
diretórios reais em `changes/` e o estado real do código.

| # | Pacote | Fase | Implementa | Estado verificável |
|---|---|---|---|---|
| 001 | `captura-multicanal` | F1 | ADR-0005/06/07/11/21 · RFC-0001 | **testado localmente**; tarefas Linktor/métricas abertas |
| 002 | `motor-autonomia-n2` | F1 | ADR-0003/04/16 · RFC-0002 | **testado localmente**; calendário comercial integrado pelo P032 |
| 003 | `triagem-web-teclado` | F1 | ADR-0002/18 | **testado localmente**; busca/paleta parcial |
| 004 | `trilha-imutavel` | F1 | ADR-0016 | **testado localmente** (+ arquivamento frio) |
| 005 | `superficie-executor` | F2 | ADR-0013 · RFC-0002 | **testado localmente** |
| 006 | `fechamento-canal-origem` | F2 | ADR-0013 | **testado localmente** |
| 007 | `portal-externo` | F2 | ADR-0013 · RFC-0006 | **testado localmente** |
| 009 | `radar-e-revisao` | F3 | ADR-0017 · RFC-0004 | **testado localmente** |
| 010 | `mineracao-regras-autonomia` | F3 | ADR-0003 · RFC-0003 | **testado localmente** |
| 011 | `laco-de-aprendizado` | F3 | ADR-0003/19 · RFC-0003 | **testado localmente** |
| 012 | `esteira-e-checklist` | F4 | ADR-0012 · RFC-0006 | **testado localmente** |
| 013 | `bloco-de-decisao` | F4 | ADR-0019 · RFC-0004 | **testado localmente** |
| 014 | `dossie-indice-hibrido` | F4 | ADR-0009/19 · RFC-0004 | **testado localmente** |
| 015 | `portal-de-instancia` | F4 | ADR-0013 · RFC-0006 | **testado localmente** |
| 018 | `gateway-de-modelos` | F1 | ADR-0020 · RFC-0007 | **testado localmente**; configuração contratual aberta |
| 019 | `linktor-real` | F2+ | ADR-0021 · ADR-0025 | **testado localmente** |
| 020 | `consulta-linguagem-natural` | F4 | RFC-0004 §3 | **testado localmente**; deploy pendente |
| 021 | `app-mobile-base` | F5 | ADR-0015 · RFC-0005 | **testado localmente no repo mobile** |
| 022 | `canal-de-voz` | F5 | ADR-0014 · RFC-0005 | **escrito**; STT/corpus real pendente |
| 023 | `modo-trajeto` | F5 | ADR-0014 · RFC-0005 | **escrito**; presença nativa pendente |
| 024 | `acompanhamento-de-grupos` | F6 | ADR-0011/20/21 | **testado localmente, parcial** |
| 025 | `repasse-com-notificacao` | F1+ | RFC-0008 · ADR-0013 | **testado localmente, parcial** |
| 026 | `lembrete-datado` | F2+ | RFC-0009 · ADR-0002/08/14 | **testado localmente, parcial** |
| 027 | `saneamento-estado-produto` | programa | documentação | **concluído** |
| 028 | `instrumentacao-piloto` | validação | G2/G7 · INV-01/07 | **testado localmente; piloto real pendente** |
| 029 | `repasse-destino-humano` | experiência | INV-04/06 · pacote 025 | **testado localmente; validação ao vivo pendente** |
| 030 | `retorno-correlacionado` | fechamento do ciclo | ADR-0029 · RFC-0010 | **testado localmente, observação WhatsApp; gate Linktor pendente** |
| 031 | `pedido-estruturado-informacao` | fechamento do ciclo | ADR-0030 · RFC-0011 | **testado localmente, parcial; gate Linktor pendente** |
| 032 | `prazos-uteis-lembretes-excecao` | fechamento do ciclo | ADR-0031 · RFC-0012 | **testado localmente; Linktor ao vivo/WhatsApp interno pendentes** |
| 033 | `consulta-completa-itens` | recuperação | ADR-0032 · RFC-0013 | **implementado e testado localmente, inclusive native/volume** |
| 034 | `revisao-resolutiva` | revisão | ADR-0033 · RFC-0014 | **implementado e testado localmente, inclusive native** |
| 035 | `resumo-diario-excecao` | despacho | ADR-0034 · RFC-0015 | **implementado e testado localmente, inclusive native** |

## Regra
Nenhum pacote pode contradizer `CONSTITUTION.md`. Se precisar, primeiro se escreve o ADR de revogação.

Os significados dos estados e a matriz por jornada estão em `../docs/ESTADO-PRODUTO.md`. Código e
testes locais não bastam para declarar uma capacidade validada ao vivo ou adotada.
