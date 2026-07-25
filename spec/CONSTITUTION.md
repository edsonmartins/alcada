# CONSTITUTION — Alçada

> Produto: **Alçada** (`alcada.app`). Decisão registrada em ADR-0022.
> Este documento é a âncora de invariantes. ADRs, RFCs e OpenSpec podem detalhar, nunca contradizer.
> Alterar um invariante exige ADR de revogação explícita, com justificativa e impacto mapeado.

---

## 1. Propósito

Alçada é o **plano de controle de decisões** de um gestor. Não é gerenciador de tarefas, não é
project management, não é CRM interno.

O problema que resolve: o backlog de um gestor não é uma lista de trabalho — é uma **fila de
decisões pendentes de terceiros**. Cada item é alguém bloqueado esperando um pedaço de julgamento
dele. A dor não é desorganização; é ausência de fila única, de alçada explícita e de mecanismo de
encolhimento.

## 2. Métrica-alvo

O sucesso do produto **não** é medido por tarefas concluídas, adoção de tela ou tempo em app.

| Métrica primária | Definição |
|---|---|
| **Taxa de desbloqueio** | tempo médio entre entrada do item e liberação de quem esperava |
| **Fração autônoma** | % de itens resolvidos sem tocar o gestor |
| **Encolhimento** | variação mensal do nº de itens que dependem do gestor |

**INV-01 — A ferramenta encolhe com o uso.** Se o volume de itens que dependem do gestor não cai
mês a mês, o produto falhou, independentemente de qualquer outro indicador. Toda feature deve
poder responder: "isso reduz o que chega nele, ou só organiza melhor o que já chega?"

## 3. Invariantes de produto

**INV-02 — O gestor nunca cadastra nada.**
Captura é passiva. O sistema extrai origem, solicitante, o que trava, prazo implícito e valor em
jogo. O gestor apenas confirma ou corrige. Nenhuma tela pode exigir criação manual como caminho
principal. Campo obrigatório permitido: nenhum, além de dono na delegação.

**INV-03 — Fila única.**
Enquanto existirem filas paralelas (grupo, e-mail, corredor, post-it, cabeça), quem grita mais alto
ganha. Todo canal captado converge para uma fila; nenhuma feature pode criar uma segunda fila.

**INV-04 — Teto de manutenção.**
Manutenção (organizar, classificar, arrastar, preencher) tem teto duro: 5 min/dia + 20 min/semana.
Tempo de **decisão** não tem teto. Feature que aumente manutenção é rejeitada mesmo que aumente
engajamento.

**INV-05 — Uma pergunta, quatro saídas.**
Toda pendência é triada por uma única pergunta ("isso precisa mesmo de você?") com quatro saídas:
Resolver, Repassar, Reservar, Repousar. Sem estimativa de esforço, sem pontuação, sem etiqueta.
Adiar existe como ação de primeira classe, exige data de retorno e declaração do que falta.

**INV-06 — Autonomia é o motor.**
Toda delegação carrega nível (N1/N2/N3). O objetivo declarado do sistema é migrar itens de
N3 → N2 → N1. Feature que não contribua para essa migração é secundária.

**INV-07 — O sistema é advogado do gestor, não auditor.**
Métricas de comportamento (adiamentos, tempo parado, gargalo) existem para diagnóstico e para
oferecer saída — nunca como placar, ranking ou cobrança. Toda superfície que exponha padrão pessoal
deve oferecer ação junto com a informação.

**INV-08 — O horizonte trimestral é blindado.**
Capacidade reservada para iniciativa própria do gestor não pode ser invadida por fluxo operacional.
O sistema avisa quando a invasão ocorre e nomeia a causa.

**INV-09 — Todo item tem contraparte.**
Quem pediu recebe estado e fechamento no canal de origem. Delegação sem tela do executor é
proibida. O produto é multi-ator por definição, não single-player com relatório.

## 4. Invariantes técnicos

**INV-10 — LLM propõe, código determinístico executa.**
Nenhuma ação irreversível ou de efeito externo é disparada por inferência. O modelo classifica,
extrai, redige e sugere. Roteamento, execução, notificação e alçada são regras determinísticas
auditáveis.

*Esclarecimento (2026-07):* a vedação alcança o **caminho de execução de efeito externo**, não o de
interpretação. Extração, classificação e redação de rascunho são caminhos de proposta e são
permitidos — desde que a decisão subsequente seja de regra, não do modelo.

**INV-11 — Trilha imutável.**
Toda transição de estado, toda sugestão do assistente (aceita ou recusada) e toda execução por
ausência geram registro append-only, com ator (humano ou sistema), timestamp e origem. A trilha é
prova, não log de conveniência.

**INV-12 — Soberania e minimização de dados.** *(emendado por ADR-0020)*
Conteúdo sensível é processado sob **controle de retenção verificável**. Inferência local permanece
obrigatória no SKU on-premise e para as classes listadas no ADR-0020 §4 (áudio do gestor, valores de
contrato, avaliação de parceiro, dossiê de decisão).

Para as demais classes, é admitido gateway de modelo de terceiro **desde que**: provedores fixados em
lista fechada, sem fallback fora dela, com retenção zero e coleta negada; e desde que o conteúdo
enviado seja minimizado e pseudonimizado antes da chamada.

A minimização não é negociável em nenhuma hipótese. Mensagem bruta e áudio bruto têm retenção curta,
finalidade declarada e expurgo automático; persiste-se o extraído estruturado.

*Redação original (obrigatoriedade universal de inferência local) revogada em 2026-07 por ADR-0020,
com justificativa e impacto comercial mapeados naquele documento.*

**INV-13 — Offline é requisito, não otimização.**
No canal móvel, captura funciona sem rede. Perder a fala do gestor uma vez custa a adoção
permanentemente. Fila local persistida, sincronização idempotente.

**INV-14 — Reversibilidade.**
Toda ação do gestor tem desfazer dentro de janela definida. Execução por ausência (N2) só dispara
efeito externo após a janela de reversibilidade fechar.

**INV-15 — Multi-tenant com isolamento por organização.**
Dados, modelos de autonomia e critérios extraídos são por organização. Nenhum aprendizado cruza
fronteira de tenant.

## 5. O que este produto não é

- Não é gerenciador de tarefas pessoais (não compete com to-do lists)
- Não é BPM: não modela processos arbitrários, modela **decisões** e um tipo de processo repetido (esteira)
- Não é chat com IA sobre trabalho
- Não é dashboard executivo: métrica sem ação associada não entra
- Não substitui a decisão do gestor de abrir mão de controle. Se a organização não aceita alçada
  delegada, o produto entrega no máximo 30% do valor — e isso deve ser dito no discovery, não
  descoberto na renovação.

## 6. Precedência

1. CONSTITUTION (este documento)
2. ADRs vigentes
3. RFCs
4. OpenSpec changes
5. Código

Conflito entre níveis resolve-se para cima. Código que contradiz ADR é bug, não fato consumado.
