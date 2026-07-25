# 013 — Bloco de decisão (dossiê + redação)

## Por quê
O item aversivo — aquele adiado três vezes — não é adiado por falta de tempo, é por **custo de
entrar** (reunir o contexto) e **custo de comunicar** (escrever o retorno). O bloco de decisão
(ADR-0019, RFC-0004) ataca os dois: dossiê pronto antes de decidir, e o rascunho do retorno depois.
É a economia no momento aversivo (INV-01: fecha o que trava há meses).

## O quê
- **Dossiê** por pendência, montado **deterministicamente** dos dados do próprio item (título, quem
  espera, o que trava, valor, prazo, temperatura/cobranças) — cada fato com sua **fonte navegável**
  (a trilha do item). Sem base recuperada, não inventa (guardrail RFC-0004).
- **Opções e consequências** por classe (Aprovar/Recusar/…), cada uma com o efeito nomeado.
- **Redação da decisão**: rascunho gerado pelo gateway de modelos (018), com variação de **tom**
  (direto/diplomático) — **rascunho editável, nada sai sem confirmação** (INV-10).
- **Decidir**: aplica a decisão de forma determinística — fecha a pendência (`FECHADA`), registra
  `DECIDIDA_NO_BLOCO` na trilha e enfileira a comunicação no outbox (o envio ao canal é efeito de
  outbox, como nos outros fechamentos).
- **Web**: tela do bloco (`/bloco/{id}`) — dossiê, opções, redação com tom, decidir e comunicar.

## INV-10 (fronteira dura)
O modelo **só redige rascunho** (caminho de proposta). O dossiê é determinístico. A decisão e o
enfileiramento do efeito são **código**, disparados pelo clique do gestor — nunca por inferência.

## Degradação honesta no piloto
No profile `demo` o transporte do gateway é stub (sem modelo real): a redação volta `disponível=false`
com um rascunho-esqueleto editável. O dossiê e o decidir funcionam normalmente (não dependem do modelo).

## Fora de escopo
- **Índice híbrido (BM25 + embeddings)** sobre a base do tenant e perguntas ao dossiê com fonte
  recuperada (RFC-0004 §1) — exige `pgvector` + ingestão; pacote próprio. Aqui o dossiê é o item.
- **Consulta em linguagem natural** (RFC-0004 §3) e **condução da sexta por IA** (§4).

## Critério de aceite
- `GET /v1/pendencias/{id}/bloco` devolve dossiê (fatos + fontes) e opções por classe.
- `redigir` devolve rascunho editável (ou `disponível=false` quando não há modelo) — nada é enviado.
- `decidir` fecha a pendência, grava `DECIDIDA_NO_BLOCO` e enfileira a comunicação (outbox).
- Nada é decidido/enviado por inferência (INV-10); tudo escopado por `org_id` (INV-15).
