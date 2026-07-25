# ADR-0023 — Stack de execução: Quarkus, Postgres único, React sem Archbase

**Status:** aceito · **Data:** 2026-07 · **Substitui:** ADR-0015

## Contexto

O ADR-0015 fixou Spring Boot 3.x + Java 21, Redis, React com Archbase e boilerplates da casa. Duas
restrições que não estavam explícitas naquele momento mudam a conclusão:

1. **O alvo de deploy é VPS pequeno** — e, no SKU Soberano, servidor do cliente. Footprint importa.
2. **A execução será conduzida por agente contra especificação.** Isso favorece bibliotecas
   mainstream, com documentação pública abundante, sobre frameworks internos que o agente não conhece.

A carga de trabalho ajuda: dezenas de itens por dia, tudo IO-bound aguardando modelo externo.
Throughput nunca será o gargalo. E como a inferência é externa (ADR-0020), não há requisito de GPU —
sem isso, VPS pequeno não seria sequer possível.

## Decisão

### Backend
**Quarkus, linha LTS, sobre Java 25.**

- **JVM em desenvolvimento** (live reload, ciclo rápido), **nativo no release** (GraalVM/Mandrel).
  Mesmo código, flag de build. Não é decisão irreversível como trocar de linguagem.
- Fixar linha LTS; não perseguir minor. Nova LTS a cada seis meses, mantida por um ano.
- Extensões: RESTEasy Reactive, Hibernate ORM com Panache (ou JDBC direto onde o SQL precisa ser
  explícito), Flyway, Scheduler, SmallRye Health/Metrics, OIDC.

### Persistência: PostgreSQL e só ele
**Redis fica fora do F1.** Postgres cobre tudo neste volume:

| Necessidade | Solução em Postgres |
|---|---|
| Fila e outbox | tabela + `SELECT ... FOR UPDATE SKIP LOCKED` |
| Scheduler persistente | tabela de jobs com claim por lock |
| Idempotência | chave única |
| Busca textual | `tsvector` |
| Embeddings e deduplicação | `pgvector` |
| Documento semiestruturado | `JSONB` |

Critério de reentrada do Redis: número medido de contenção ou latência que justifique — não
antecipação. Um processo a menos vale mais, num VPS pequeno, que qualquer ganho teórico de cache.

### Frontend
**React 19 + TypeScript + Mantine v9 + Vite. Sem Archbase.**

- SPA pura, sem SSR. É app autenticado, sem SEO.
- Estado de servidor: **TanStack Query** — cache, invalidação e mutação otimista, que é exatamente o
  que a janela de desfazer (INV-14) precisa.
- Estado de UI: Zustand ou contexto nativo. Sem Redux.
- Formulários: React Hook Form + Zod, nos poucos pontos que têm formulário.
- Roteamento: TanStack Router.

### Mobile
**Flutter permanece.** O ADR-0014 exige STT no dispositivo, áudio em background e CarPlay/Android
Auto — nenhum deles é atendido por PWA. Sem boilerplate interno.

### Observabilidade e deploy
VictoriaMetrics + Grafana + Loki + Tempo, como antes. Container único, Docker Compose no VPS,
Traefik. SKU on-premise entrega o binário nativo.

### Módulos
Fronteiras internas mantidas como no ADR-0015 (`identidade`, `captura`, `triagem`, `autonomia`,
`regras`, `esteira`, `assistente`, `notificacao`, `metricas`), com teste de arquitetura que falha se
um módulo acessar internals de outro.

## Orçamento de memória (VPS de 2 GB)

| Processo | Alvo |
|---|---|
| Aplicação (Quarkus nativo) | 80–120 MB |
| PostgreSQL tunado | 400–500 MB |
| Traefik | ~30 MB |
| Sistema | 150–250 MB |
| **Total** | **~700–900 MB** |

Referência de comparação: em modo nativo, Quarkus fica em torno de 70 MB de RSS contra 149 MB do
Spring Boot também nativo — e a diferença é bem maior contra Spring Boot em JVM.

## O que se abandona, explicitamente

**`archbase-java-boilerplate`.** Perda pequena neste caso: o domínio é atípico para boilerplate —
trilha append-only, outbox, scheduler, máquina de estados. Quase nada é CRUD com tela gerada.

**`archbase-react`.** Perda maior em teoria, pequena na prática aqui. A interface é lista, painel de
detalhe, workspace de três colunas e quadro — não formulário CRUD em série. O protótipo
(`prototipo/alcada-sistema.html`) já demonstra o escopo real da UI.

**Contrapartida:** o produto fica independente da versão dos frameworks internos, um desenvolvedor
novo não precisa aprender Archbase para contribuir, e o agente de código trabalha sobre bibliotecas
que ele conhece bem — o que reduz alucinação de API e retrabalho.

## Consequências
- (+) Footprint compatível com VPS pequeno e com o SKU on-premise
- (+) Stack mainstream: melhor para execução por agente e para contratar
- (+) Uma única dependência de infraestrutura (Postgres) até haver número que justifique outra
- (−) Segundo padrão de frontend na casa, sem reuso de volta para o Archbase
- (−) Sai do Spring, onde está a maior profundidade acumulada da equipe
- (−) Build nativo lento no CI — mitigado testando em JVM e compilando nativo só no release
- (−) Reflexão é o ponto de dor do native image: preferir cliente HTTP simples com serialização
  explícita no gateway de modelos, em vez de SDK pesado

## Revisão
Reavaliar se: o build nativo se mostrar inviável no CI, alguma extensão essencial faltar, ou o
volume passar a exigir infraestrutura adicional.
