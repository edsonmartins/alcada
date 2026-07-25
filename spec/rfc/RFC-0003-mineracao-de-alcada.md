# RFC-0003 — Mineração de regras de autonomia e proposta de regra

**Status:** proposto · **Implementa:** ADR-0003, ADR-0012, ADR-0019

## Objetivo
Transformar padrão decisório observado em regra explícita, e critério tácito em checklist.

## Duas mineração distintas

### A. Regra de alçada (classe de decisão)
Agrupa decisões por assinatura: `{classe, faixa_de_valor, tipo_solicitante, escopo}`.
Candidata a regra quando, numa janela de 90 dias:

- `n >= 15` ocorrências
- consistência de desfecho `>= 95%`
- **zero reversões** posteriores
- variância de valor dentro da faixa

Saída: proposta com **evidência clicável** — os N casos navegáveis (ADR-0019). Sem os casos, não
propõe.

### B. Checklist de esteira (critério tácito)
Sobre as últimas N passagens pela mesma etapa, correlaciona **o que foi apontado** com o desfecho.
Critério candidato quando aparece em ≥ 50% das reprovações e é expressável como verificação objetiva.

Marca separadamente critérios de **julgamento** (não viram checklist, permanecem com o gestor).

## Laço de aprendizado
Após cada decisão relevante, **uma** pergunta:

> "Você reprovou o Panorama. Foi por causa dos campos fiscais?"
> `[sim]` `[não, foi outra coisa]` `[não pergunte mais isso]`

Regras: no máximo 1 pergunta por decisão; no máximo 3 por semana; `não pergunte` silencia a classe
permanentemente. A recusa vai para a trilha e é sinal negativo para a mesma proposta.

## Anti-padrões explicitamente evitados
- propor regra a partir de poucos casos (ruído vira política)
- aprender através de tenants (INV-15)
- promover automaticamente sem confirmação humana (INV-10)

## API
```
GET  /v1/regras/propostas         -> candidatas com evidência
POST /v1/regras                   -> cria regra a partir de proposta
POST /v1/regras/{id}/desativar
GET  /v1/esteiras/{id}/checklist/propostas
```
