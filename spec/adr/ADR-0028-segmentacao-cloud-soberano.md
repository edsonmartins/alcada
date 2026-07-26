# ADR-0028 — Segmentação de oferta: Alçada Cloud × Alçada Soberano

**Status:** proposto (decisão comercial — aguarda ratificação) · **Data:** 2026-07
**Encerra:** G8 (DECISOES-ABERTAS) · **Relacionado:** ADR-0020, ADR-0010, ADR-0023, RFC-0007

## Contexto
O ADR-0020 adotou o OpenRouter como gateway de LLM. Consequência incontornável: no SKU que usa
OpenRouter, o conteúdo **cruza duas fronteiras administrativas** (aplicação → OpenRouter → provedor
downstream) e **não há roteamento in-region no Brasil**. Não dá para vender "soberania de dados" nesse
SKU — mesmo com `data_collection: deny`, `zdr: true` e `allow_fallbacks: false`.

A stack já carrega os dois caminhos: `organizacao.sku ∈ {CLOUD, SOBERANO}`; o roteamento por
`Sensibilidade` manda `RESTRITA` para inferência local (`AdaptadorLocal`, hoje stub) e o resto para o
externo. Falta a **decisão comercial**: o que cada oferta promete, para quem, e a que preço — antes da
primeira proposta.

## Decisão

### 1. Duas ofertas, com promessas distintas e honestas

| | **Alçada Cloud** | **Alçada Soberano** |
|---|---|---|
| Inferência | OpenRouter (lista `only`, ZDR, no-training) | Local, no perímetro do cliente |
| Conteúdo sai do país? | Sim (sem residência nacional) | **Não** |
| Promessa central | método + minimização + ZDR/no-training | **soberania e residência nacional** |
| Qualidade de modelo | fronteira (melhor) | limitada ao que roda local |
| Custo de operação | baixo (VPS pequeno) | alto (servidor capaz/GPU no cliente) |
| Público | PME, ciclo de venda curto | regulado / exigência de residência |
| Deploy | container único gerido | binário nativo on-premise (ADR-0023) |

### 2. O que **Cloud pode** prometer (e o que **não pode**)
- **Pode**: minimização antes de toda chamada (ADR-0010), `only`+`deny`+`zdr`+`no-fallbacks`,
  re-hidratação local, nenhum plugin do OpenRouter, classes `RESTRITA` sempre locais.
- **Não pode**: "seus dados nunca saem", residência nacional, soberania. O discurso de Cloud é
  **método + guardrails**, nunca soberania. Vender soberania em Cloud é falso e cria risco jurídico.

### 3. O que **Soberano** entrega e cobra
- Nenhum conteúdo cruza o perímetro; residência nacional; encaixe regulatório.
- Em troca: modelo local (qualidade abaixo da fronteira), infraestrutura do cliente, preço maior,
  operação on-premise. Exige concluir o `AdaptadorLocal` real (hoje stub) antes de vender.

### 4. Recomendação de posicionamento e preço (a ratificar)
- **Piloto atual = Cloud** (é o que está implementado e no ar). Não prometer soberania ao piloto.
- Preço **Cloud**: por assento/mês, baixo, ancorado no tempo de gestor devolvido (o ROI é 1–1,5 h/dia).
- Preço **Soberano**: setup + licença anual + assento, refletindo infra e operação; venda consultiva.
- **Gate antes da 1ª venda Soberano**: `AdaptadorLocal` real + escolha do stack de modelo local.

## O que precisa ser verdade antes da primeira venda
1. Discurso comercial separado por SKU (Cloud não fala soberania).
2. Lista `only` de provedores homologados fechada para o Cloud (**G9**, contratual).
3. Para Soberano: `AdaptadorLocal` implementado e stack de modelo local escolhido.

## Consequências
- (+) Cada oferta promete só o que cumpre; elimina o risco de vender soberania sobre OpenRouter.
- (+) A arquitetura já suporta os dois (SKU + roteamento por sensibilidade); é decisão comercial, não
  reescrita.
- (−) Soberano tem custo de engenharia real pendente (inferência local) antes de existir como produto.
- (−) Duas ofertas = dois discursos, dois contratos, dois RIPDs (ver G5).

## Revisão
Reabrir se surgir roteamento in-region no Brasil pelo OpenRouter (mudaria o que o Cloud pode
prometer) ou se o custo de inferência local cair a ponto de unificar as ofertas.
