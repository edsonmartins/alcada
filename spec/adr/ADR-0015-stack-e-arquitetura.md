# ADR-0015 — Stack e arquitetura de execução

**Status:** ~~aceito~~ **SUBSTITUÍDO por ADR-0023** (2026-07)

> Mantido por rastreabilidade. A stack vigente é Quarkus + Postgres único + React sem Archbase.
> Ver `ADR-0023-stack-quarkus.md`.

**Data original:** 2026-07

## Decisão

**Backend.** Java 21 + Spring Boot 3.x, **monólito modular** com fronteiras por contexto
(`captura`, `triagem`, `autonomia`, `alcada`, `esteira`, `assistente`, `notificacao`, `identidade`).
Extração para serviço próprio só quando houver razão de escala ou isolamento, não por estética.

**Persistência.** PostgreSQL 15+ como fonte da verdade. Trilha em tabela append-only particionada.
Redis para cache de sessão, rate limiting, idempotência e locks curtos.

**Assíncrono.** Padrão **outbox transacional** + worker para todo efeito externo (notificação,
execução N2, fechamento de canal). Garantia at-least-once com idempotência por chave de operação.

**Agendamento.** Scheduler persistente para janelas N2, retorno de itens dormindo e SLA de esteira.
Nenhum timer em memória.

**Web.** React 19 + TypeScript + Mantine v9 + Archbase. Rotas por superfície de ator.

**Mobile.** Flutter, offline-first com fila local persistida e sincronização idempotente.

**IA.** Camada de inferência local (ver ADR-0010) atrás de interface de porta; provedor é detalhe
de implementação.

**Observabilidade.** VictoriaMetrics + Grafana + Loki + Tempo. Métricas de produto (encolhimento,
fração autônoma, recall de captura) são de primeira classe, não só técnicas.

**Deploy.** Docker Swarm + Traefik; opção on-premise para cliente que exigir soberania total.

## Consequências
- (+) Aderente à stack da casa; reaproveita Archbase e boilerplates.
- (+) Monólito modular reduz custo operacional numa fase em que o produto ainda muda de forma.
- (−) Fronteiras internas exigem disciplina; sem ela, vira monólito comum.
