# CLAUDE.md — Alçada

Instruções permanentes para o Claude Code neste repositório. Valem em toda sessão.

---

## 1. Fonte da verdade e precedência

Este repositório é **spec-driven**. A ordem de precedência é absoluta:

```
CONSTITUTION.md  >  adr/  >  rfc/  >  openspec/changes/  >  código
```

Conflito resolve-se **para cima**. Código que contradiz ADR é bug, não fato consumado.

**Se uma tarefa exigir contrariar a CONSTITUTION ou um ADR vigente: pare, explique o conflito e
proponha um ADR de revogação.** Não implemente e não contorne. Precedente: ADR-0020 emendou o INV-12
por esse caminho.

Antes de escrever qualquer código, leia: `CONSTITUTION.md`, `docs/GLOSSARIO.md`, o ADR e o RFC
citados no pacote OpenSpec da vez.

## 2. Linguagem ubíqua

Use os termos de `docs/GLOSSARIO.md` em código, tabelas, endpoints e mensagens. Não invente sinônimo.

Convenções de nome que colidem e precisam de disciplina (ADR-0022):

| Uso | Forma |
|---|---|
| Produto | **Alçada**, maiúscula |
| Conceito de autoridade | alçada, minúscula |
| Regra do sistema | **regra de autonomia** — nunca "regra de alçada" |

Vocabulário do domínio, em código: `Pendencia`, `Classe`, `Horizonte`, `Saida`, `Delegacao`,
`Nivel` (N1/N2/N3), `Adiamento`, `Temperatura`, `RegraAutonomia`, `Esteira`, `Instancia`,
`BlocoDecisao`, `Trilha`.

## 3. Invariantes que o código precisa honrar

Estes são os que um agente costuma violar sem perceber:

1. **INV-10 — LLM propõe, código determinístico executa.** Nenhuma chamada de modelo no caminho de
   execução de efeito externo. O modelo entrega classe/extração/rascunho; a regra decide e executa.
2. **INV-11 — Trilha append-only.** Sem `UPDATE`, sem `DELETE` em `trilha`. Correção é evento de
   compensação. Todo evento tem ator (`HUMANO:{id}` | `SISTEMA:{regra}` | `ASSISTENTE:{modelo,versão}`).
3. **INV-02 — O gestor nunca cadastra.** Criação manual existe como escape, nunca como caminho
   principal, e é métrica de falha.
4. **INV-14 — Reversibilidade.** Efeito externo só sai depois de fechada a janela.
5. **INV-15 — Multi-tenant.** Toda query carrega `org_id`. Nenhum aprendizado cruza tenant.

## 4. Regras técnicas não negociáveis

- **Outbox transacional** para todo efeito externo. Transição de estado e escrita no outbox na
  **mesma transação**. Nunca chamada externa dentro do request.
- **Idempotência** em toda escrita de efeito externo, por `Idempotency-Key` ou
  `(pendencia_id, transicao, ocorrencia)`.
- **Nenhum timer em memória.** Scheduler persistente em tabela, com claim por lock e retry exponencial.
- **Reflexão é o inimigo do native image.** No gateway de modelos, cliente HTTP simples com
  serialização explícita — não SDK pesado. Toda classe que exigir reflexão precisa de justificativa.
- **Tempo em UTC** no banco; apresentação em `America/Sao_Paulo`; prazos ancorados a horário
  comercial do tenant.
- **Gateway de modelos** (RFC-0007): nenhum módulo de domínio conhece OpenRouter. Chamador declara
  `Sensibilidade`, o gateway decide destino. Política `only` + `allow_fallbacks:false` +
  `data_collection:deny` + `zdr:true` + `require_parameters:true` é fixa no adaptador.
- **Minimizador antes de qualquer chamada de modelo** (ADR-0020 §3): pseudonimizar nomes, remover
  identificadores, truncar ao trecho relevante. Re-hidratação local.
- **Captura seletiva** (ADR-0011): nunca varredura completa de canal. Log auditável da proporção
  processada.
- **Canais só via Linktor** (ADR-0021). A Alçada não fala com WhatsApp nem com servidor de e-mail.

## 5. Stack  (ADR-0023)

**Backend:** Quarkus, linha LTS, sobre Java 25. Monólito modular. JVM em desenvolvimento, **nativo no
release**. Extensões: RESTEasy Reactive, Panache (ou JDBC direto onde o SQL precisa ser explícito),
Flyway, Scheduler, SmallRye, OIDC.

**Persistência: PostgreSQL e só ele.** Sem Redis no F1. Fila e outbox com `SKIP LOCKED`, scheduler em
tabela de jobs, idempotência por chave única, busca com `tsvector`, embeddings com `pgvector`.
Introduzir outra dependência de infraestrutura exige número medido, não antecipação.

**Web:** React 19 + TypeScript + Mantine v9 + Vite. **Sem Archbase.** SPA pura, sem SSR.
Estado de servidor com TanStack Query (mutação otimista atende a janela de desfazer, INV-14),
estado de UI com Zustand, formulários com React Hook Form + Zod, rotas com TanStack Router.
Referência visual: `prototipo/alcada-sistema.html`.

**Mobile:** Flutter 3.27, offline-first, sem boilerplate interno.

**Observabilidade:** VictoriaMetrics, Grafana, Loki, Tempo.
**Deploy:** container único, Docker Compose + Traefik em VPS pequeno; binário nativo no SKU on-premise.

**Orçamento de memória:** aplicação ≤ 120 MB de RSS em nativo. Se uma escolha empurrar acima disso,
pare e avise antes de seguir.

Módulos (fronteiras internas reais, não pacotes decorativos):

```
identidade · captura · triagem · autonomia · regras · esteira
assistente · notificacao · metricas
```

Dependência entre módulos só por porta/interface publicada. Nada de acesso a tabela de outro módulo.

## 6. Ciclo de trabalho

Para cada pacote em `openspec/changes/`:

1. **Ler** `proposal.md`, `design.md`, `spec.md`, `tasks.md` e os ADRs/RFCs citados
2. **Propor plano** curto: ordem das tasks, migrations, riscos, o que ficará de fora
3. **Aguardar confirmação** antes de implementar
4. **Implementar** task a task, marcando `- [x]` em `tasks.md` conforme conclui
5. **Testar**: cada cenário `WHEN/THEN` de `spec.md` vira ao menos um teste automatizado, com o
   mesmo nome do cenário
6. **Relatar** o que mudou, o que não foi feito e por quê

Não pule o passo 2. Não implemente além do pacote em curso.

## 7. Testes

- Todo cenário de `spec.md` tem teste correspondente e rastreável pelo nome
- Motor de autonomia: testar reinício da aplicação, corrida entre intervenção e vencimento, fuso na
  virada do dia
- Captura: teste de vazamento — nenhum identificador direto atravessa o minimizador
- Gateway: provedor sem `json_schema` deve **falhar**, nunca degradar para `json_object`
- Multi-tenant: teste de isolamento em toda consulta

## 8. O que não fazer

- Não criar feature que não esteja em um pacote OpenSpec aprovado
- Não adicionar arrastar-e-soltar, taxonomia customizável pelo usuário ou tela puramente
  contemplativa (ADR-0018)
- Não expor métrica de comportamento individual sem ação associada (ADR-0017)
- Não usar chat aberto como superfície do assistente (ADR-0019)
- Não emitir notificação de "novo item na entrada" no mobile — reintroduz a reatividade que o
  produto combate
- Não inventar endpoint fora de `docs/API.md` sem atualizar o documento na mesma mudança

## 9. Idioma

Domínio, banco, endpoints, commits e documentação em **português**. Palavras-chave de linguagem e
bibliotecas em inglês, naturalmente. Sem Portuglish em nome de classe (`PendenciaService`, não
`PendencyService`).
