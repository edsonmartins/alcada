# Superfície de API

**Base:** `/v1` · **Auth:** OAuth2/OIDC (ArchGuard) · **Tenant:** header `X-Org-Id`, validado contra o token
**Idempotência:** header `Idempotency-Key` obrigatório em toda escrita de efeito externo
**Erros:** RFC 7807 (`application/problem+json`)

## Recursos

### Pendências
```
GET    /v1/pendencias?status=&horizonte=&dono=&q=&page=
GET    /v1/pendencias/{id}
POST   /v1/pendencias                      # escape manual — métrica de falha (ADR-0005)
POST   /v1/pendencias/{id}/resolver        { nota? }
POST   /v1/pendencias/{id}/delegar         { dono_id, nivel, prazo }
POST   /v1/pendencias/{id}/reservar        { agendado_para, gerar_dossie: bool }
POST   /v1/pendencias/{id}/repousar        { volta_em }
POST   /v1/pendencias/{id}/adiar           { volta_em, o_que_falta: NADA|INSUMO|TERCEIRO }
POST   /v1/pendencias/{id}/intervir        # interrompe N2, devolve para ENTRADA
POST   /v1/pendencias/{id}/desfazer        # dentro da janela de reversibilidade
GET    /v1/pendencias/{id}/trilha
POST   /v1/pendencias/{id}/desfundir       { cobranca_id }   # reverte deduplicação
```

`GET /v1/pendencias` devolve, por item:
`id, titulo, classe, horizonte, status, quemEspera, temperatura, baixaConfianca,`
`oQueTrava, valorEmJogo (número|null), prazoImplicito (ISO|null), criadaEm (ISO)`.

### Hoje e fila
```
GET    /v1/hoje                            # no máximo 3 itens + justificativa por item
GET    /v1/radar                           # diagnóstico organizacional (ADR-0017), leitura pura
GET    /v1/revisao-semanal                 # roteiro conduzido da sexta
```

`GET /v1/radar` (pacote 009) → `dependeDoGestor{qtd,total,pct}`, `rodandoSemVoce`,
`adiados[{id,titulo,adiadoCount,oQueTrava,quemEspera,valorEmJogo}]`,
`piorEspera{pendenciaId,titulo,dias,quemEspera}`,
`autonomia{deliberada,porAusencia,devolvida,escalada,promovida}` (separados — ADR-0024),
`fechamentoCanal{entregue,falho,impossivel}` (ADR-0025),
`encolhimento[{semana,entraram,fecharam}]` (8 semanas, fluxo).

`GET /v1/revisao-semanal` → `entrada{qtd,itens[]}`, `adiados[]` (idem radar),
`podeVirarRegra[{classe,ocorrencias}]` (dica, não regra),
`resumoSemana{resolvidas,executadas,delegadas,escaladas,devolvidas,fechadas}`.

### Delegações (superfície do executor)
```
GET    /v1/delegacoes?status=              # itens delegados ao usuário autenticado
POST   /v1/delegacoes/{id}/propor          { proposta }
POST   /v1/delegacoes/{id}/concluir        { resultado }
POST   /v1/delegacoes/{id}/devolver        { motivo }
```

### Regras de autonomia (pacote 010, RFC-0003 §A)
```
GET    /v1/regras                          # regras ativas
GET    /v1/regras/propostas                # candidatas mineradas + evidência navegável
POST   /v1/regras                          { classe, nivel, donoId }   # aceitar (humano confirma, INV-10)
POST   /v1/regras/propostas/silenciar      { classe }
POST   /v1/regras/{id}/desativar
```

`GET /v1/regras` → `[{id, classe, nivel, donoId, criadaEm}]`.
`GET /v1/regras/propostas` → `[{classe, ocorrencias, consistencia, nivelSugerido, donoSugerido,
casos:[{pendenciaId, titulo, desfecho, valorEmJogo}]}]`.
`POST /v1/regras`: `409` se já há regra ativa da classe; `422` se `nivel` excede
`classe_decisao.nivel_maximo`. A regra criada por classe é aplicada pelo motor de captura
(`ROTEADA_POR_REGRA`). Assinatura fina `{faixa, tipo_solicitante, escopo}` fica para pacote futuro
(exige motor de aplicação por faixa).

### Laço de aprendizado (pacote 011, RFC-0003 / ADR-0019)
```
GET    /v1/aprendizado/perguntas                    # gera sob demanda + lista abertas (com evidência)
POST   /v1/aprendizado/perguntas/{id}/responder     { resposta: SIM|AGORA_NAO|NAO_PERGUNTAR }
```

`GET` → `[{id, classe, nivelSugerido, donoSugerido, ocorrencias, casos[]}]` (uma aberta por classe,
teto 3/semana). `responder`: `SIM` cria a regra (dono sugerido ou quem respondeu); `AGORA_NAO` recusa
sem silenciar; `NAO_PERGUNTAR` silencia a classe (010). Trilha: `SUGESTAO_EMITIDA/ACEITA/RECUSADA/
SILENCIADA`.

### Esteira
```
GET    /v1/esteiras
GET    /v1/esteiras/{id}/instancias?etapa=
POST   /v1/esteiras/{id}/instancias
POST   /v1/instancias/{id}/avaliar         { checklist_versao, resultados[] }
POST   /v1/instancias/{id}/avancar
GET    /v1/esteiras/{id}/checklist
POST   /v1/esteiras/{id}/checklist         # nova versão (nunca update)
```

### Assistente
```
POST   /v1/assistente/dossie/{pendencia_id}          # monta/atualiza dossiê
POST   /v1/assistente/perguntar                      { pendencia_id, pergunta } -> { resposta, fontes[] }
POST   /v1/assistente/redigir                        { pendencia_id, opcao, tom } -> { rascunho }
POST   /v1/assistente/consultar                      { pergunta } -> { resposta, consulta_gerada }
POST   /v1/assistente/aprendizado/{id}/responder     { sim|agora_nao|nunca }
```

### Captura
```
POST   /v1/captura/eventos                 # webhook autenticado por fonte
POST   /v1/captura/linktor                 # webhook do Linktor (HMAC por fonte, ADR-0021/0025)
POST   /v1/captura/audio                   # texto transcrito no dispositivo + metadados
GET    /v1/fontes
POST   /v1/fontes                          # declaração de canal (ADR-0011)
POST   /v1/fontes/{id}/desativar
```

### Comandos (voz e mobile)
```
POST   /v1/comandos                        { intencao, campos, origem, client_ts, idempotency_key }
POST   /v1/comandos/lote                   # sincronização de fila offline
GET    /v1/trajetos/{id}/resumo            # resumo com desfazer por item
POST   /v1/trajetos/{id}/confirmar         # libera efeitos externos represados
```

### Portal externo
```
POST   /v1/pendencias/{id}/portal          # emite link assinado (interno, tenant) -> { link } (ADR-0013)
POST   /v1/portal/{tokenId}/revogar        # revoga token (interno, tenant)
GET    /p/{token}                          # público, sem login: estado/prazo/o que falta (no-index)
POST   /p/{token}/autoavaliacao            # declaração de conformidade (com a esteira, pacote 014)
```

### Métricas de produto
```
GET    /v1/metricas/encolhimento?de=&ate=
GET    /v1/metricas/gargalo
GET    /v1/metricas/captura                # recall, precisão, descarte
```

## Eventos (outbox → assinantes)
```
pendencia.criada          pendencia.fundida         pendencia.triada
delegacao.criada          delegacao.executada       delegacao.executada_por_ausencia
delegacao.escalada        alcada.criada             instancia.avancou
item.fechado              trajeto.confirmado
```

## Contratos de erro relevantes
| Código | Situação |
|---|---|
| `409 pendencia.estado_invalido` | transição não permitida pela máquina de estados |
| `409 janela.expirada` | desfazer fora da janela de reversibilidade |
| `422 alcada.inelegivel` | classe não elegível ao nível solicitado (ADR-0004) |
| `423 movimento.bloqueado` | ação recusada em trajeto (ADR-0014) |
