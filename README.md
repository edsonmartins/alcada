# Alçada

> **O plano de controle de decisões de um gestor.**
> Não é gerenciador de tarefas, não é project management, não é CRM interno.

Alçada captura passivamente tudo que espera decisão de um gestor, roteia o que não
precisa dele e **reduz mês a mês o volume que precisa**. A diferença para um gerenciador
de tarefas é o objetivo: um to-do organiza melhor a mesma sobrecarga; a Alçada existe
para encolher a sobrecarga.

**Frase de teste do produto:** se ao fim de 90 dias o número de itens que dependem do
gestor não caiu, o produto não entregou.

---

## 1. O problema

O backlog de um gestor não é uma lista de trabalho — é uma **fila de decisões pendentes
de terceiros**. Cada item é alguém bloqueado esperando um pedaço de julgamento dele. A
dor não é desorganização; é a ausência de três coisas:

- **fila única** — enquanto existirem filas paralelas (grupo, e-mail, corredor, post-it,
  cabeça), quem grita mais alto ganha;
- **alçada explícita** — o time não sabe até onde pode agir sem o gestor;
- **mecanismo de encolhimento** — nada faz o volume que chega nele cair com o tempo.

## 2. Como funciona

### Captura passiva (o gestor nunca cadastra)
As mensagens chegam pelos canais onde o time já vive (WhatsApp, e-mail — via a camada de
canais **Linktor**). O sistema extrai origem, solicitante, o que trava, prazo implícito e
valor em jogo. O gestor apenas confirma ou corrige. Criação manual existe só como
**escape** e é medida como falha de captura, nunca como caminho principal.

### Uma pergunta, quatro saídas
Toda pendência é triada por uma única pergunta — *"isso precisa mesmo de você?"* — com
quatro saídas, mais o adiar como ação de primeira classe:

| Saída | Significado |
|---|---|
| **Resolver** | o gestor decide agora |
| **Repassar** | delega a alguém, com nível de autonomia |
| **Reservar** | agenda para um bloco de decisão |
| **Repousar** | tira da frente até uma data ou nova cobrança |
| *Adiar* | exige data de retorno **e** declaração do que falta para decidir |

Sem estimativa de esforço, sem pontuação, sem etiqueta. O "Hoje" mostra **no máximo 3 itens**.

### Autonomia é o motor (N1 · N2 · N3)
Toda delegação carrega um nível. O objetivo declarado do sistema é migrar itens de
**N3 → N2 → N1** — de "depende de mim" para "resolve sozinho".

- **N3** — executor propõe, gestor decide (ainda depende do gestor).
- **N2** — executor propõe e **executa por ausência**: se o gestor não intervém dentro de
  uma janela de silêncio, a decisão vale. É o coração do produto.
- **N1** — regra de autonomia decide sozinha, sem passar pelo gestor.

### Silêncio como aprovação, com rede de segurança
No N2, o gestor aprova **pelo silêncio** (ADR-0004). Mas:

- toda ação tem **desfazer** dentro de uma janela definida (INV-14);
- o efeito externo só dispara **depois** de a janela de reversibilidade fechar;
- no silêncio de *ambos* (executor não propõe, gestor não responde), o item **escala** —
  nunca executa em branco;
- o executor tem tela própria: delegação sem tela do executor é proibida (INV-09).

### Trilha imutável (prova, não log)
Toda transição de estado, toda sugestão do assistente (aceita ou recusada) e toda
execução por ausência geram registro **append-only**, com ator (`HUMANO` / `SISTEMA` /
`ASSISTENTE`), timestamp e origem. É prova de por que cada decisão saiu como saiu.

### Contraparte sem login
Quem pediu (solicitante) e quem espera de fora (contraparte externa) recebem estado e
fechamento **no canal de origem** ou por um **portal externo** sem login — para pararem
de cobrar por fora, que é o que infla o backlog percebido.

## 3. Máquina de estados da pendência

```
                 ┌──────────► FECHADA ◄────────┐
                 │                             │
CAPTADA ─► ENTRADA ─┬─► DELEGADA ─┬─► FECHADA (executada / por ausência)
                    │             └─► ENTRADA (gestor interrompeu N2)
                    ├─► AGENDADA ────► FECHADA (decidida no bloco)
                    ├─► DORMINDO ────► ENTRADA (data de retorno ou cobrança)
                    └─► ENTRADA (adiada, com volta_em e o_que_falta)
```

`AGUARDANDO_JANELA` não é estado da pendência — é sub-estado da **Delegacao**. A janela
pertence ao contrato daquela delegação específica; a UI e as métricas leem
`Pendencia.status`, que permanece grosso e estável.

## 4. Para quem é

| Persona | Papel |
|---|---|
| **P1 — Gestor** | usuário primário; ponto de bloqueio de dezenas de fluxos |
| **P2 — Executor** | recebe delegação; precisa de contrato claro (o que, até quando, o que acontece no silêncio) |
| **P3 — Solicitante** | pediu algo e não sabe se foi visto |
| **P4 — Contraparte externa** | espera e **não tem como cobrar** (não está no grupo) |
| **P5 — Admin** | configura fontes, retenção, tenants, integrações; responsável por LGPD |

## 5. Princípios inegociáveis (invariantes)

A fonte da verdade é a `spec/CONSTITUTION.md`. Os que mais moldam o produto:

- **INV-01 — A ferramenta encolhe com o uso.** Se o que depende do gestor não cai mês a
  mês, o produto falhou, independentemente de qualquer outro indicador.
- **INV-02 — O gestor nunca cadastra.** Captura é passiva; criação manual é métrica de falha.
- **INV-03 — Fila única.** Nenhuma feature pode criar uma segunda fila.
- **INV-04 — Teto de manutenção.** Organizar/classificar/arrastar: 5 min/dia + 20 min/semana.
  Tempo de *decisão* não tem teto.
- **INV-07 — Advogado do gestor, não auditor.** Métrica de comportamento só aparece com
  ação junto; nada de placar, ranking ou cobrança.
- **INV-10 — LLM propõe, código determinístico executa.** Nenhum efeito externo é
  disparado por inferência; o modelo classifica/extrai/redige, a regra decide e executa.
- **INV-11 — Trilha imutável.** Sem `UPDATE`/`DELETE`; correção é evento de compensação.
- **INV-12 — Soberania e minimização.** Conteúdo minimizado e pseudonimizado antes de
  qualquer chamada de modelo; retenção verificável; inferência local para as classes
  sensíveis do ADR-0020.
- **INV-14 — Reversibilidade.** Todo ato tem desfazer; efeito externo só após a janela fechar.
- **INV-15 — Multi-tenant.** Toda query carrega `org_id`; nenhum aprendizado cruza tenant.

## 6. O que este produto **não** é

Não é to-do pessoal, não é BPM (modela **decisões**, não processos arbitrários), não é
chat com IA sobre trabalho, não é dashboard executivo (métrica sem ação não entra), e não
substitui a decisão do gestor de abrir mão de controle — se a organização não aceita
alçada delegada, o produto entrega no máximo ~30% do valor, e isso se diz no discovery.

Nas telas: **nunca** arrastar cartão, campo livre obrigatório, nem criação manual como
caminho principal (ADR-0018).

---

## 7. Arquitetura

**Spec-driven.** A especificação em `spec/` é a fonte da verdade, com precedência absoluta:

```
CONSTITUTION.md  >  adr/  >  rfc/  >  openspec/changes/  >  código
```

Conflito resolve-se para cima: código que contradiz um ADR é bug, não fato consumado.

**Stack (ADR-0023):**
- **Backend:** Quarkus 3.35.4 (LTS) sobre **Java 25** (GraalVM). Monólito modular. JVM em
  desenvolvimento, **binário nativo no release** (orçamento ≤ 120 MB de RSS).
- **Persistência: PostgreSQL e só ele.** Fila e outbox com `FOR UPDATE SKIP LOCKED`,
  scheduler persistente em tabela de jobs (nenhum timer em memória), trilha particionada
  append-only, busca com `tsvector`, embeddings com `pgvector`. Sem Redis, sem Kafka no F1.
- **Web:** React 19 + TypeScript + Mantine 9 + Vite (SPA pura, sem SSR). TanStack
  Query/Router, Zustand, React Hook Form + Zod. Servida same-origin pelo próprio Quarkus.
- **Canais:** só via **Linktor** (ADR-0021) — a Alçada não fala direto com WhatsApp nem
  com servidor de e-mail. **Modelos:** via gateway OpenRouter/DeepInfra sob política fixa
  `only` + `allow_fallbacks:false` + `data_collection:deny` + `zdr:true` (RFC-0007).

**Módulos** (fronteiras reais, verificadas por ArchUnit — cada um expõe `port` e esconde
`internal`; ninguém acessa `internal` alheio):

```
Domínio:     identidade · captura · triagem · autonomia · regras · esteira
             assistente · notificacao · metricas
Plataforma:  multitenancy · trilha · outbox · scheduler · gateway
```

## 8. Estado da implementação

| Fase | Escopo | Status |
|---|---|---|
| **F1 — núcleo** | captura, gateway de modelos, trilha, motor de autonomia N2, triagem | ✅ implementado |
| **F2 — contraparte** | tela do executor, fechamento no canal de origem, portal externo | ✅ implementado |
| Integrações | Linktor real (entrada/saída, HMAC) · gateway OpenRouter/DeepInfra real | ✅ implementado |
| Prontidão de deploy | seed idempotente · SPA same-origin · trilha + countdown na web | ✅ implementado |
| **F3 — encolhimento** | mineração de regras, radar, revisão de sexta | ✅ testado localmente; adoção pendente |
| **F4 — profundidade** | bloco, dossiê, consulta, esteira e checklist | ✅ testado localmente; adoção pendente |
| **F5 — mobilidade** | app Flutter, voz, modo trajeto, offline | 🧪 escrito; validação em dispositivo/STT pendente |
| Incrementos 024–026 | grupos, repasse externo, lembrete e Google Calendar | 🧪 parciais; ver tarefas abertas |

`Implementado` nesta tabela não significa validado com usuário. A fotografia verificável e as
pendências por jornada estão em `spec/docs/ESTADO-PRODUTO.md`.

Ordem deliberada: **contraparte antes de IA avançada** — N2 sem tela do executor é
mecanismo sem apoio. Cobertura atual: testes JVM (ArchUnit, guarda de `org_id`, trilha
imutável, outbox, scheduler, motor de ausência, captura, triagem, portal, Linktor) e
Vitest no front.

## 9. Rodar

**Pré-requisitos:** JDK 25 (GraalVM CE), Maven, PostgreSQL 17, Node + pnpm (para o front).

Banco e role (não-superusuário, para o `REVOKE` da trilha valer):
```sql
CREATE ROLE alcada LOGIN PASSWORD 'alcada';
CREATE DATABASE alcada      OWNER alcada;
CREATE DATABASE alcada_test OWNER alcada;
```

Backend:
```bash
mvn quarkus:dev                    # desenvolvimento (JVM, live reload) — porta 8080
mvn verify                         # build + testes (fronteiras, guarda, trilha, outbox, scheduler, motor)
mvn flyway:validate                # valida migrations
mvn package -Dnative -DskipTests   # binário nativo (release)
```

Front + SPA servida same-origin pelo Quarkus:
```bash
cd web && pnpm install && pnpm build   # gera web/dist
cd .. && mvn package                   # o profile "spa" embute web/dist no app; sem CORS
```

Bootstrap de um tenant para demonstração (parametrizado por ambiente, idempotente,
_fail-closed_ — ver `deploy/README.md`):
```bash
psql "$ALCADA_DB_URL" -f deploy/seed.sql
```

## 10. Documentação

Toda em `spec/` (português; palavras-chave e bibliotecas em inglês):

- `spec/CONSTITUTION.md` — invariantes (âncora, precedência máxima)
- `spec/docs/PRODUTO.md` — especificação de produto (personas, superfícies, roadmap)
- `spec/docs/GLOSSARIO.md` — linguagem ubíqua do domínio
- `spec/docs/API.md` — contrato dos endpoints
- `spec/docs/PILOTO.md` — pacote de validação do piloto (gate G2)
- `spec/docs/ESTADO-PRODUTO.md` — implementação × validação × adoção, com pendências reais
- `spec/docs/PLANO-IMPLEMENTACAO.md` — plano mestre das próximas ondas de produto
- `spec/adr/` — decisões de arquitetura (ADR-0001…0025)
- `spec/rfc/` — desenhos técnicos (pipeline de captura, motor de autonomia, gateway…)
- `spec/prototipo/alcada-sistema.html` — referência visual
- `spec/CLAUDE.md` — regras permanentes de engenharia deste repositório
