# Design — 002 motor de autonomia N2

Referência: `rfc/RFC-0002-motor-de-autonomia.md`.

## Tabelas
```sql
delegacao(id, pendencia_id, dono_id, nivel, prazo, janela, status,
          proposta, proposta_em, criada_em)
job_agendado(id, chave_idem, tipo, executar_em, tentativas, status)
outbox(id, agregado_id, tipo_evento, payload, publicado_em)
classe_decisao(id, org_id, nome, nivel_maximo, valor_limite, janela, escalonamento, elegivel_n1)
ausencia(id, pessoa_id, de, ate)
```

## Invariantes de implementação
- `job_agendado.chave_idem = (pendencia_id, transicao, ocorrencia)` — único
- efeito externo **sempre** via `outbox`, nunca chamada direta no fluxo de request
- transição de estado e escrita no outbox na **mesma transação**
- relógio em UTC; prazos ancorados a horário comercial do tenant

## Modo ausência
Ao criar delegação, se `dono_gestor` estiver em `ausencia`, força `nivel = N3`. Delegações N2 em
aberto têm `prazo` estendido e o executor é notificado.
