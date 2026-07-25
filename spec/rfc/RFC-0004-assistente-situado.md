# RFC-0004 — Assistente situado: dossiê, redação e consulta

**Status:** proposto · **Implementa:** ADR-0009, ADR-0019, ADR-0010

## Objetivo
Reduzir o custo de **entrar** na decisão difícil e o custo de **comunicar** o resultado.

## 1. Dossiê ancorado
Contexto agregado por pendência, montado antes do bloco: contrato, histórico da entidade, cobranças,
submissões anteriores, decisões similares e seus desfechos.

Perguntas respondidas apenas com recuperação sobre a base do tenant:
- índice híbrido (BM25 + embeddings locais) sobre trilha, mensagens extraídas, documentos anexos,
  registros de ERP autorizados
- **toda resposta cita fonte navegável**; sem passagem recuperada acima do limiar, responde
  "não encontrei isso na base" — nunca completa de memória
- o assistente **corrige a premissa** da pergunta quando a base contradiz (ex.: "não foi maio, foi 08/07")

## 2. Redação da decisão
Gera o retorno no canal de origem, condicionado a: opção escolhida, destinatário, histórico de
relacionamento e tom.

- variações de tom (direto / diplomático) sem alterar o conteúdo factual
- fatos citados no texto vêm do dossiê, com verificação de consistência antes de exibir
- o texto é **rascunho editável**; nada é enviado sem confirmação explícita (INV-10)

## 3. Consulta em linguagem natural
Tradução de pergunta para consulta estruturada sobre a fila (não RAG livre):
"quanto está parado esperando por mim", "o que trava por causa do financeiro".
Execução determinística sobre o modelo relacional; o LLM só monta a consulta e redige a resposta.

## 4. Condução da revisão de sexta
Roteiro sequencial, um item por vez: candidatos a morrer, candidatos a regra, esteira parada,
invasão do trimestre. O gestor responde com um toque.

## Guardrails
| Regra | Implementação |
|---|---|
| Sem fonte, sem resposta | limiar de recuperação; resposta padrão de ausência |
| Nada executa por inferência | camada de ação separada (INV-10) |
| Sugestão sempre auditável | trilha registra emissão e desfecho (INV-11) |
| Conteúdo sensível não sai | roteamento por sensibilidade (ADR-0010) |

## Métricas
- taxa de aceitação do rascunho sem edição relevante
- tempo médio de bloco de decisão antes/depois do dossiê
- itens aversivos (3+ adiamentos) fechados por trimestre
