# 018 — Gateway de modelos

## Por quê
A extração da captura (001) depende de inferência com schema estrito, e o ADR-0020 emendou o INV-12
para admitir gateway de terceiro **sob controle de retenção verificável e minimização não
negociável**. Nenhum módulo de domínio pode conhecer OpenRouter, provedor ou modelo: a política vive
no gateway, não no chamador. Por isso 018 vem antes de 001 na ordem de execução.

## O quê
Porta única `ModelGateway` (extrair, redigir, classificar, embutir), roteamento por `Sensibilidade`,
adaptador OpenRouter com política fixa e não parametrizável, fronteira do minimizador/re-hidratador,
tratamento de indisponibilidade como erro com fila de reprocesso, forçamento local para SKU Soberano
e para as classes que não saem, e observabilidade sem prompt nem resposta.

## Fora de escopo
- O **minimizador em si** vive em `captura` (ADR-0020 §3, RFC-0007) e é entregue no pacote 001; aqui
  se define a **fronteira** (o gateway nunca recebe texto não minimizado quando `INTERNA`) e o teste
  de vazamento.
- Stack de **inferência local** (SKU Soberano): 018 define a porta e o roteamento; o adaptador local
  é implementado quando o SKU Soberano entrar.

## Critério de aceite
- Provedor sem `json_schema` estrito **falha** — nunca degrada para `json_object`.
- `allow_fallbacks:false`: indisponibilidade vira **erro tratado**, a tarefa vai para fila de
  reprocesso e a captura nunca é perdida (item entra com `confianca = null`).
- Tenant com SKU **Soberano** e classe `RESTRITA` nunca saem para o gateway externo.
- Nenhum identificador direto atravessa a fronteira do minimizador (teste com corpus real).
- Re-hidratação não vaza token de pseudônimo entre itens.
- Log de chamada não contém prompt nem resposta — só `mensagem_id` como referência.
- A lista `only` é parametrizada (começa com um provedor); mudar a lista é evento auditado.
