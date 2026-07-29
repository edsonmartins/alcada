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
GET    /v1/pendencias/{id}/bloco           # dossiê + opções (pacote 013)
POST   /v1/pendencias/{id}/bloco/redigir   { opcao, tom }    # rascunho editável (modelo; proposta)
POST   /v1/pendencias/{id}/decidir         { opcao, texto }  # fecha + DECIDIDA_NO_BLOCO + outbox
POST   /v1/pendencias/{id}/dossie/perguntar { pergunta }     # recuperação híbrida (014)
```

Perguntas ao dossiê (014, RFC-0004 §1): `POST .../dossie/perguntar` → `{encontrou, resposta,
fontes:[{fonteTipo, fonteRef, trecho}]}`. Recuperação híbrida BM25 (`tsvector`) + embeddings
(`pgvector`, cosseno) sobre `documento_indice`; **cita fonte**; abaixo do limiar `encontrou=false`
("não encontrei isso na base"). Sem modelo de embedding, recupera por BM25.

Bloco de decisão (013, RFC-0004): `GET .../bloco` → `{titulo, classe, dossie:[{rotulo,valor}],
opcoes:[{chave,rotulo,consequencia}]}` (dossiê determinístico; fonte = a trilha). `redigir` →
`{rascunho, disponivel, aviso}` (modelo só propõe; `disponivel=false` degrada sem gateway).
`decidir` fecha a pendência, grava `DECIDIDA_NO_BLOCO` e enfileira `decisao.comunicada` (INV-10:
decidir é ação do gestor, nunca inferência; `409` se já fechada).

`GET /v1/pendencias` devolve, por item:
`id, titulo, classe, horizonte, status, quemEspera, temperatura, baixaConfianca,`
`oQueTrava, valorEmJogo (número|null), prazoImplicito (ISO|null), criadaEm (ISO),`
`origemGrupo (nome do grupo|null quando não veio de grupo), cobrancas (int)`.
`origemGrupo` alimenta o rótulo "grupo X" na Entrada; `cobrancas` alimenta
"já te cobraram Nx" (024). `quemEspera` já vem por primeiro nome (ADR-0011 emenda).

### Hoje e fila
```
GET    /v1/hoje                            # no máximo 3 itens + justificativa por item
GET    /v1/radar                           # diagnóstico organizacional (ADR-0017), leitura pura
GET    /v1/revisao-semanal                 # roteiro conduzido da sexta
```

### Consulta e canal móvel
```
POST   /v1/consulta                        { pergunta }                 # consulta NL sobre a fila (020, RFC-0004 §3)
POST   /v1/comandos                        { comandos:[Comando] }       # sync do canal móvel (021, RFC-0005)
POST   /v1/voz/transcrever                  { audioBase64, formato?, idioma? }  # STT na nuvem (022, ADR-0026)
```

`POST /v1/voz/transcrever` (022): áudio em base64 → `{texto}`, via gateway (Whisper no
OpenRouter; chave só no servidor). Indisponível → **503**, e o app degrada para o STT
on-device (INV-13). Só SKU Cloud (áudio de decisão sai do perímetro — ADR-0020/0028);
classe RESTRITA nunca sai.

`POST /v1/consulta` (020): pergunta livre → template de whitelist → SQL determinístico
(INV-10/INV-15). Resposta `{pergunta, template, resposta, itens:[{id,titulo,classe,valorEmJogo}]}`;
fora da whitelist, template `DESCONHECIDO` ("não sei responder isso sobre a fila").

`POST /v1/comandos` (021): lote **idempotente** por `(org_id, comandoId)` — reenvio devolve o
resultado gravado, não re-executa (INV-13). Cada `Comando{comandoId, intencao, pendenciaId?, campos}`
mapeia para a ação determinística existente (INV-10); intenções: `RESOLVER, REPASSAR, RESERVAR,
REPOUSAR, ADIAR, REGISTRAR, CONSULTAR`. Resposta `{resultados:[{comandoId, status:
OK|IGNORADO|RECUSADO|ERRO, detalhe?, pendenciaId?, consulta?}]}`. Pendência que já saiu da fila →
`IGNORADO` (não erro); `REGISTRAR` (escape) nunca é ignorado; `CONSULTAR` traz `consulta`.

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

### Esteira (pacote 012, ADR-0012 / RFC-0006)
```
GET    /v1/esteiras                          # esteiras + etapas
POST   /v1/esteiras                          { nome, etapas:[{ordem,nome,donoId,sla,etapaDoGestor}] }
GET    /v1/esteiras/{id}/instancias?etapa=
POST   /v1/esteiras/{id}/instancias          { entidadeExterna }
POST   /v1/instancias/{id}/avaliar           { resultados:[{criterioChave,resultado}], apontamentos:[{texto,tipo}] }
POST   /v1/instancias/{id}/avancar
GET    /v1/esteiras/{id}/checklist           # versão vigente + critérios
POST   /v1/esteiras/{id}/checklist           { criterios:[{chave,descricao,tipo,obrigatorio}] }  # nova versão
GET    /v1/esteiras/{id}/checklist/propostas # mineração §B: objetivos ≥50% + julgamento à parte
```

`avaliar` → `{desfecho, pendenciaId}`. `APROVADA` avança a instância sem pendência; `REPROVADA`/
`PENDENTE_JULGAMENTO` geram pendência classe `ESTEIRA` em `ENTRADA` com o resultado anexado (trilha
`CAPTADA`). Checklist é versionado (nunca update). Propostas: apontamento `OBJETIVO` em ≥50% das
reprovações vira candidato; `JULGAMENTO` fica à parte (não vira checklist).

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
`POST /v1/captura/linktor` (024): quando a mensagem veio de grupo, o envelope traz
`data.group.id` (chat_jid; ausente em 1:1) e `data.message.senderId` = o indivíduo
que falou. Só grupos **selecionados** têm o conteúdo ingerido (ver Grupos).
`data.message.mentions` (JIDs mencionados; ausente sem menção): menção num grupo
acompanhado faz o worker avaliar a janela na hora, sem esperar o debounce (C5).
Ao ativar um grupo (`PUT /v1/grupos/{id}` ativa=true), a Alçada publica um aviso
no grupo via Linktor (`POST /channels/{channelId}/groups/{groupId}/messages`);
só depois de publicado (bot visível, ADR-0011 §2) o conteúdo do grupo é capturado (C6).

### Grupos (pacote 024 — seleção/opt-in, ADR-0011 §1)
```
GET    /v1/grupos                          # grupos que o bot viu, com {grupoId, nome, ativa, ultimoVisto}
PUT    /v1/grupos/{grupoId}                { ativa, finalidade? }   # opt-in: escolher controlar
```
Grupo não selecionado (`ativa=false`) é **descartado** no webhook — só é registrado
(id/nome) para o gestor poder escolher; o conteúdo não é ingerido.

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

# Instância de esteira (pacote 015, RFC-0006)
POST   /v1/instancias/{id}/portal          # emite link assinado da instância (gestor) -> { token }
POST   /v1/instancias/portais/{tokenId}/revogar
GET    /pi/{token}                          # público: esteira, etapa, prazo previsto, o que falta (no-index)
POST   /pi/{token}/autoavaliacao   { declaracoes:[{criterioChave, conforme}] }  # contraparte declara conformidade
```

`GET /pi/{token}` → `{esteiraNome, etapaAtualNome, entrouEm, prazoPrevisto, oQueFalta:[{chave,descricao}]}`
(critérios OBJETIVOS da etapa do gestor). Token só-hash; resposta uniforme para inválido/expirado/
revogado; nunca expõe deliberação/decisores/outras contrapartes. Autoavaliação informa o gestor (INV-10).

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
