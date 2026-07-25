# Alçada — núcleo

Plano de controle de decisões de um gestor. Este repositório é **spec-driven**: a
especificação em `spec/` é a fonte da verdade (precedência em `spec/CLAUDE.md §1`).

Esqueleto da **Sessão 2 (bootstrap)**: estrutura, não funcionalidade. Sem regra de
negócio, sem endpoint de domínio.

## Stack (ADR-0023)
- **Quarkus 3.35.4 (LTS) sobre Java 25.** JVM em desenvolvimento, **nativo no release**.
- **PostgreSQL e só ele.** Fila/outbox com `SKIP LOCKED`, scheduler em tabela de jobs.
- Monólito modular; fronteiras por pacote, verificadas por ArchUnit.

## Módulos
Domínio: `identidade · captura · triagem · autonomia · regras · esteira · assistente ·
notificacao · metricas`. Plataforma (transversal): `plataforma.{multitenancy, trilha,
outbox, scheduler}`. Cada módulo expõe `port` e esconde `internal`; ninguém acessa
`internal` alheio.

## Pré-requisitos
- JDK 25 (GraalVM CE) e Maven.
- PostgreSQL 17. Em dev/test usa-se o Postgres local; `docker-compose.yml` sobe um
  Postgres isolado (única dependência de infraestrutura — sem Redis, sem Kafka).

Bancos e role (não-superusuário, para o REVOKE da trilha valer):
```sql
CREATE ROLE alcada LOGIN PASSWORD 'alcada';
CREATE DATABASE alcada      OWNER alcada;
CREATE DATABASE alcada_test OWNER alcada;
```

## Rodar
```bash
mvn quarkus:dev                 # desenvolvimento (JVM, live reload) — porta 8080
mvn verify                      # build + testes (ArchUnit, guarda org_id, trilha, outbox, scheduler)
mvn flyway:validate             # valida migrations
mvn package -Dnative -DskipTests    # binário nativo (release)
```

## Invariantes exercitados no esqueleto
- **INV-11** trilha append-only: trigger + `REVOKE` bloqueiam `UPDATE`/`DELETE`
  (`TrilhaImutavelTest`).
- **INV-15** multi-tenant: `GuardaOrgId` recusa query a dado de tenant sem `org_id`
  no predicado (`GuardaOrgIdTest`).
- **Outbox transacional**: efeito não sai fora da transação; entrega ao menos uma vez,
  idempotente (`OutboxTransacionalTest`).
- **Scheduler persistente**: job sobrevive a reinício e executa uma vez
  (`SchedulerReinicioTest`).
