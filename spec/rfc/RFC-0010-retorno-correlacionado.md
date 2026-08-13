# RFC-0010 — Retorno correlacionado de executor e contato externo

**Status:** proposto · **Implementa:** ADR-0029 · **Pacote:** 030

## 1. Objetivo

Fazer uma resposta recebida pelo canal voltar à delegação que a originou, sem heurística de
identidade e sem permitir que texto livre execute efeito.

## 2. Contrato Linktor necessário

Envio direto e resposta em conversa aceitam metadata adicional preservada:

```json
{"metadata":{"source":"alcada","idempotency_key":"...","alcada_correlation":"token"}}
```

Quando uma mensagem recebida for resposta/continuação daquele envio:

```json
{"type":"message.received","data":{"context":{"alcada_correlation":"token"},"message":{...}}}
```

O Linktor não interpreta o token. Apenas o associa ao fio e o devolve. Antes do código de produção,
o contrato precisa de fixture e teste de integração no Linktor.

## 3. Persistência

### `correlacao_retorno`

- `id`, `org_id`, `delegacao_id`, `token_hash`, `canal`, `destino_hash`;
- `criada_em`, `expira_em`, `revogada_em`;
- UNIQUE `(org_id, token_hash)`;
- não guarda token claro nem endereço.

### `retorno_delegacao`

- `id`, `org_id`, `delegacao_id`, `mensagem_id_hash`, `tipo`, `trecho_minimizado`;
- `recebido_em`, `processado_em`, `estado`;
- UNIQUE `(org_id, mensagem_id_hash)`;
- append-only para conteúdo; classificação/correção entram como eventos próprios ou compensação.

Tipos propostos: `INFORMACAO`, `PROPOSTA`, `RESULTADO`, `COBRANCA`, `CONTESTACAO`, `PEDIDO_PRAZO`,
`SEM_EFEITO`, `INCONCLUSIVO`.

## 4. Fluxo

1. ao criar aviso, código gera token e persiste somente hash;
2. outbox leva token ao adaptador; Linktor recebe como metadata;
3. webhook autentica HMAC e extrai `data.context.alcada_correlation`;
4. hash resolve exatamente uma correlação ativa no tenant da Fonte;
5. código confirma que canal e autor correspondem ao destino do contrato;
6. reentrega por `message.id` termina em no-op;
7. texto é minimizado e persistido;
8. classificador pode propor tipo; indisponibilidade vira `INCONCLUSIVO`;
9. regra determinística anexa ao dossiê/trilha e, se acionável, devolve Pendência à Entrada;
10. gestor decide no fluxo normal. Nenhuma resposta livre chama `concluir`, `propor` ou execução N2.

## 5. Transição determinística

| Tipo proposto | Pendência | Delegação | Próxima ação |
|---|---|---|---|
| INFORMAÇÃO / PROPOSTA / CONTESTAÇÃO / PEDIDO_PRAZO | ENTRADA | permanece, execução automática suspensa | gestor avalia |
| RESULTADO | ENTRADA | permanece | gestor confirma conclusão |
| COBRANÇA | estado atual; temperatura +1 | permanece | ação aparece conforme temperatura |
| SEM_EFEITO | estado atual | permanece | somente evidência |
| INCONCLUSIVO | ENTRADA | permanece, execução automática suspensa | gestor classifica |

Suspender execução significa cancelar logicamente jobs futuros pela verificação de estado/flag
persistida; nunca apagar job. A retomada é uma ação explícita.

## 6. Concorrência

- retorno e virada N2 disputam lock da delegação;
- se o retorno adquirir lock antes, suspende a execução;
- se a execução já publicou efeito, o retorno vira nova evidência na Pendência fechada e oferece
  compensação, nunca reabre silenciosamente;
- mensagem repetida é idempotente pelo hash do id externo.

## 7. Retenção

O retorno minimizado segue a retenção do dossiê; bruto permanece no Linktor. Expirada a correlação,
a resposta segue captura normal. Revogar delegação revoga correlação, preservando prova histórica.

## 8. Observabilidade

- correlacionados, inválidos, expirados e rejeitados por autor/canal;
- latência entre recebimento e volta à Entrada;
- nenhuma métrica individual de executor;
- alerta de contrato Linktor quebrado vem com teste de integração/ação corretiva.

## 9. Rollout

1. Linktor propaga metadata em ambiente de integração;
2. Alçada grava correlação, mas apenas observa retornos;
3. comparar correlação com casos manuais;
4. habilitar anexação e despertar por tenant;
5. só depois medir queda de recobrança.
