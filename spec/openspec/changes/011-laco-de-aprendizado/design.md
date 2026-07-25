# Design — 011 laço de aprendizado

## Determinístico, humano confirma
Sem modelo. A pergunta nasce de uma candidata do `Mineracao` (010, SQL puro). A regra só é criada
quando o gestor responde `sim` (INV-10).

## Persistência
`V14__pergunta_aprendizado.sql`:
```
pergunta_aprendizado (
  id uuid pk, org_id uuid, classe text, pendencia_ref uuid,   -- caso representativo (p/ trilha)
  status text CHECK (ABERTA|ACEITA|RECUSADA|SILENCIADA),
  criada_em timestamptz default now(), respondida_em timestamptz, respondida_por uuid)
```
Índice parcial único `(org_id, classe) WHERE status='ABERTA'` → **1 pergunta aberta por classe**.

## Geração (GET /v1/aprendizado/perguntas)
Lazy, determinística, escopada por org:
1. candidatas = `Mineracao.propostas(org)` (já exclui silenciadas e classes com regra ativa).
2. exclui classes com pergunta **ABERTA** (dedupe) e com **RECUSADA nesta semana** (não re-perguntar).
3. respeita o **teto semanal**: `criadas_na_semana < 3` (semana seg–dom, America/Sao_Paulo).
4. para cada classe elegível (até o teto): cria `pergunta_aprendizado (ABERTA)` com
   `pendencia_ref` = caso mais recente; grava trilha `SUGESTAO_EMITIDA` nesse caso.
5. retorna as **abertas**, cada uma com a evidência da proposta correspondente (casos, nivelSugerido,
   donoSugerido) — ADR-0019.

## Resposta (POST /v1/aprendizado/perguntas/{id}/responder {resposta})
- `SIM` → `Regras.criar(classe, nivelSugerido, donoSugerido)` (422 se dono ausente ou nível acima do
  teto; 409 vira ACEITA idempotente se já há regra); status `ACEITA`; trilha `SUGESTAO_ACEITA`.
- `AGORA_NAO` → status `RECUSADA`; trilha `SUGESTAO_RECUSADA`. Sinal negativo: não re-pergunta a
  classe na semana; a proposta continua visível em `/alcadas` (o gestor disse "agora não", não "nunca").
- `NAO_PERGUNTAR` → status `SILENCIADA`; trilha `SUGESTAO_SILENCIADA`; `Regras.silenciar(classe)`
  (010) → some de perguntas **e** de propostas.

## Trilha por caso representativo
`SUGESTAO_*` exige `pendencia_id`; a pergunta é por classe, então usa-se o `pendencia_ref` (caso mais
recente da classe). A trilha desse item registra o ciclo da sugestão (auditável).

## Web (/hoje)
ADR-0019 "onde o gestor estiver" → a superfície diária. Um card com a pergunta:
"As decisões de **{classe}** viraram rotina. Transformar em regra ({nível} · dono)?" +
`ver evidência (N casos → trilha)` + `[Sim, criar regra]` `[Agora não]` `[Não perguntar isso]`.
Uma pergunta por vez (a próxima aparece depois de responder).

## Multi-tenant / reflexão
Predicados com `org_id` (INV-15; GuardaOrgId conhece `pergunta_aprendizado`). DTOs via `Response` →
`@RegisterForReflection`.

## Fora do design
- Pergunta ancorada em critério (§B checklist). Aqui a pergunta é sobre a regra da classe.
- Cooldown configurável de recusa (fixo em "a semana" nesta fase).
