# Design — 012 esteira, checklist e mineração §B

## Migration V15
```
esteira(id, org_id, nome, criada_em)
etapa(id, org_id, esteira_id, ordem, nome, dono_id, sla interval, etapa_do_gestor bool)
checklist(id, org_id, etapa_id, versao int, criada_em)         UNIQUE(org_id, etapa_id, versao)
criterio(id, org_id, checklist_id, chave, descricao, tipo CHECK(OBJETIVO|JULGAMENTO), obrigatorio bool)
instancia(id, org_id, esteira_id, entidade_externa, etapa_atual_id, status CHECK(EM_ANDAMENTO|CONCLUIDA), entrou_em)
avaliacao(id, org_id, instancia_id, etapa_id, checklist_versao int,
          desfecho CHECK(APROVADA|REPROVADA|PENDENTE_JULGAMENTO), avaliador_id, avaliada_em)
apontamento(id, org_id, avaliacao_id, texto, tipo CHECK(OBJETIVO|JULGAMENTO))
```
Todas em `DADOS_TENANT` do GuardaOrgId. Sem alteração no vocabulário da trilha (a pendência gerada
usa `CAPTADA`, como qualquer entrada).

## Regra de avanço (RFC-0006)
`avaliar(instancia, resultados[], apontamentos[])`:
1. carrega a versão vigente do checklist da etapa; grava `avaliacao` + `apontamento[]`.
2. **desfecho**:
   - todo `OBJETIVO obrigatorio` com resultado `OK` **e** nenhum `JULGAMENTO` pendente/apontado →
     `APROVADA`.
   - algum `OBJETIVO obrigatorio` `FALHOU` → `REPROVADA`.
   - há `JULGAMENTO` pendente/apontado (sem falha objetiva) → `PENDENTE_JULGAMENTO`.
3. **efeito**:
   - `APROVADA` → avança a instância (`etapa_atual` = próxima por `ordem`; sem próxima → `CONCLUIDA`).
     **Não gera pendência** (o valor do módulo).
   - `REPROVADA`/`PENDENTE_JULGAMENTO` → cria `pendencia` classe `ESTEIRA` em `ENTRADA`, com o
     resultado anexado (`o_que_trava` = resumo; carga = avaliacao_id), trilha `CAPTADA`. A instância
     fica parada na etapa até `avancar`.

`avancar(instancia)`: move para a próxima etapa (ou `CONCLUIDA`) — usado quando o gestor resolveu a
pendência gerada.

## Mineração §B (RFC-0003)
`GET /v1/esteiras/{id}/checklist/propostas` (leitura pura, por etapa do gestor):
- universo = `avaliacao` `REPROVADA` da etapa (janela 90 dias).
- para cada `apontamento` (texto normalizado) de tipo `OBJETIVO`: fração de reprovações em que
  aparece. Candidato quando `>= 50%` e ainda não é critério da versão vigente.
- apontamentos `JULGAMENTO` são retornados numa lista **separada** ("não viram checklist").
- sem reprovações suficientes (mín. configurável, padrão 4), não propõe.

Aceite = `POST /v1/esteiras/{id}/checklist` com os critérios (nova versão; nunca update). INV-10: o
gestor confirma; nada vira critério sozinho.

## API
```
GET  /v1/esteiras                              -> esteiras + etapas
POST /v1/esteiras            {nome, etapas:[{ordem,nome,donoId,sla,etapaDoGestor}]}
GET  /v1/esteiras/{id}/instancias?etapa=
POST /v1/esteiras/{id}/instancias  {entidadeExterna}
POST /v1/instancias/{id}/avaliar   {resultados:[{criterioChave,resultado}], apontamentos:[{texto,tipo}]}
POST /v1/instancias/{id}/avancar
GET  /v1/esteiras/{id}/checklist               -> versão vigente + critérios
POST /v1/esteiras/{id}/checklist   {criterios:[{chave,descricao,tipo,obrigatorio}]}  # nova versão
GET  /v1/esteiras/{id}/checklist/propostas     -> candidatos (objetivo ≥50%) + julgamento à parte
```

## Multi-tenant / reflexão / native
Predicados com `org_id`. DTOs via `Response` → `@RegisterForReflection`. `interval`/`timestamptz`
normalizados. A pendência gerada respeita o INV-15 e o outbox só entra quando houver efeito externo
(não há aqui — a pendência é interna).

## Fora do design
- Portal de instância + autoavaliação (RFC-0006) — pacote próprio.
- SLA no score de priorização — integra depois.
- Idempotência de `avaliar` por chave — reavaliar cria nova `avaliacao` (histórico); a de-duplicação
  fina fica para incremento.
