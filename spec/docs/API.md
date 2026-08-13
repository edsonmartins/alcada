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
POST   /v1/pendencias/{id}/resolver        { nota?, lembrete?: { quando, texto, comCalendario? } }
POST   /v1/pendencias/{id}/delegar         { dono_id, nivel, prazo }
POST   /v1/pendencias/{id}/reservar        { agendado_para, gerar_dossie: bool }
POST   /v1/pendencias/{id}/repousar        { volta_em }
POST   /v1/pendencias/{id}/adiar           { volta_em, o_que_falta: NADA|INSUMO|TERCEIRO }
POST   /v1/pendencias/{id}/pedidos-informacao { contatoId, pergunta, prazo } -> { id }
POST   /v1/pendencias/{id}/lembrete/cancelar  # desiste do compromisso (RFC-0009)
POST   /v1/pendencias/{id}/intervir        # interrompe N2, devolve para ENTRADA
POST   /v1/pendencias/{id}/desfazer        # dentro da janela de reversibilidade
GET    /v1/pendencias/{id}/trilha
POST   /v1/pendencias/{id}/desfundir       { cobranca_id }   # reverte deduplicação
GET    /v1/pendencias/{id}/bloco           # dossiê + opções (pacote 013)
POST   /v1/pendencias/{id}/bloco/redigir   { opcao, tom }    # rascunho editável (modelo; proposta)
POST   /v1/pendencias/{id}/decidir         { opcao, texto }  # fecha + DECIDIDA_NO_BLOCO + outbox
POST   /v1/pendencias/{id}/dossie/perguntar { pergunta }     # recuperação híbrida (014)
```

### Calendário comercial (P032, ADMIN)

```text
GET /v1/calendario-comercial
PUT /v1/calendario-comercial
    { timezone, diasUteis:[1..7], inicio:"09:00", fim:"18:00",
      feriados:[{data:"2026-09-07", nome:"Independência"}] }
```

O calendário mantém o expediente no horário local da organização e persiste jobs em UTC. Somente
`ADMIN` altera; leitura e cálculo são isolados por organização. Os lembretes N2 de 50% e 90% usam
tempo útil e viram no-op após proposta ou estado terminal.

### Preferência e resumo diário por exceção (P032/P035)

```text
GET /v1/preferencias-notificacao
PUT /v1/preferencias-notificacao
    { canal:"EMAIL", resumoInicio:"09:00"?, resumoFim:"17:00"?, ativa:true }
```

O job gera um retrato imutável por gestor, período e data local. O envelope contém até três itens de
Hoje, até três exemplos (mais total) de N2 prestes a executar, retornos que exigem decisão e
escalonamentos, além de estimativa opcional derivada da mediana histórica. Vazio é silêncio.

A entrega exige `pessoa.email` e uma fonte `EMAIL` ativa com `linktor_channel_id` no mesmo tenant;
sem ambos, não inventa destino. Repetir ou concorrer no mesmo período mantém um retrato e uma
mensagem lógica. Não existe notificação de “novo item” ou por ocorrência individual.

```text
POST /v1/retornos/{id}/decisao
    Idempotency-Key: obrigatório
    { decisao:"APLICAR"|"REJEITAR" }
```

`APLICAR` considera a evidência, registra `RETORNO_AVALIADO` e não escolhe Saída nem produz efeito
externo. Repetição da mesma decisão/chave retorna `204`; decisão divergente retorna `409`.

`POST .../resolver` com `lembrete` (RFC-0009): fecha o item **e** guarda o compromisso que sobrou
("resolvi, mas marquei a reunião pra quinta"). `quando` é ISO-8601 **com fuso**, já resolvido pelo
chamador; o lembrete vira uma pendência que dorme até a data e volta pela Entrada (fila única, sem
caixa de lembretes). `204`; `400 alcada:lembrete.invalido` se faltar `quando`/`texto` ou a data não
for ISO-8601; `422 alcada:lembrete.invalido` no passado ou a mais de 12 meses — e aí **o item não
fecha**. Com `comCalendario`, o compromisso também vai para a agenda do gestor: sai pelo outbox
(`EVENTO_CALENDARIO`) e só **depois da janela** `alcada.calendario.janela` (default 5 min), para o
desfazer chegar antes do evento existir (INV-14). Sem calendário conectado, a trilha registra
`FALHA_COMPROMISSO` e o lembrete continua valendo dentro do Alçada.

`POST .../lembrete/cancelar`: o gestor desiste do compromisso. Fecha o lembrete (mesmo dormindo) e
limpa a agenda — se o evento ainda não saiu, o efeito é descartado do outbox; se já existe, enfileira
`CANCELAR_EVENTO_CALENDARIO`. Idempotente; `409 alcada:pendencia.estado_invalido` se o id não é um
lembrete.

### Calendário do gestor (RFC-0009, OAuth por pessoa)
```
GET    /v1/calendario?redirectUri=&state=  # { conectado, provedor?, escopo?, urlConsentimento? }
POST   /v1/calendario                      { codigo, redirectUri }   # troca o consentimento
DELETE /v1/calendario                      # desconecta e esquece os tokens
```
Com `redirectUri`, o `GET` devolve também a **URL do consentimento** — montada no servidor, que é
quem sabe o client id e o escopo mínimo (`calendar.events`: escreve eventos, **não lê** a agenda).
O `state` vem do cliente e volta pelo provedor (anti-CSRF); a tela confere antes de trocar o código.
O adaptador real é ligado por `alcada.calendario.real` (piloto e prod); sem isso valem os stubs.
A conta é **do gestor**, não do tenant: quem conecta é o `X-Pessoa-Id` do contexto. Os tokens ficam
**cifrados** no banco (AES-GCM, `alcada.cripto.chave`) e **nunca** voltam por esses endpoints —
`GET` diz apenas se há conta, de qual provedor e com que escopo. `422
alcada:calendario.consentimento_invalido` quando o código expirou ou é de outro app.

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
POST   /v1/revisao-semanal/sessoes         # inicia ou retoma sessão resolutiva
GET    /v1/revisao-semanal/sessoes/{id}    # relê as seis etapas no estado canônico
POST   /v1/revisao-semanal/sessoes/{id}/concluir
POST   /v1/revisao-semanal/sessoes/{id}/regras/{classe}/{aceitar|recusar|observar}
POST   /v1/revisao-semanal/sessoes/{id}/promocoes  {classe,donoId,nivelAtual}
POST   /v1/revisao-semanal/sessoes/{id}/protecao-agenda {inicio,duracaoMinutos}
```

### Consulta e canal móvel
```
POST   /v1/consulta                        { pergunta }                 # consulta NL sobre a fila (020, RFC-0004 §3)
POST   /v1/comandos                        { comandos:[Comando] }       # sync do canal móvel (021, RFC-0005)
POST   /v1/voz/transcrever                  { audioBase64, formato?, idioma? }  # STT na nuvem (022, ADR-0026)
POST   /v1/voz/interpretar                  { texto, contexto?, itens? }        # fala livre → intenção (022)
```

`POST /v1/voz/transcrever` (022): áudio em base64 → `{texto}`, via gateway (Whisper no
OpenRouter; chave só no servidor). Indisponível → **503**, e o app degrada para o STT
on-device (INV-13). Só SKU Cloud (áudio de decisão sai do perímetro — ADR-0020/0028);
classe RESTRITA nunca sai.

`POST /v1/voz/interpretar` (022): fala + contexto + fila → `{intencao, pendenciaId, titulo, donoId,
donoNome, nivel, tituloNovo, resposta, frase, precisaConfirmar, candidatosDono:[{id,nome,tipo,canal}],
termoFalado, contatoId, contatoCanal, podeRegistrarContato, lembreteQuando, lembreteTexto}`. O modelo **propõe**; o app confirma
(INV-10). No `REPASSAR`, o nome falado é resolvido contra pessoas **e** contatos externos (RFC-0008):
um casamento único vira `donoId` (interno) **ou** `contatoId`+`contatoCanal` (externo, nunca os dois);
mais de um devolve `candidatosDono` com `tipo` `PESSOA|CONTATO`; nenhum devolve a lista conhecida,
`termoFalado` (para aprender o apelido ao escolher) e `podeRegistrarContato`. Canal e endereço de um
contato novo **não** saem da fala — o app os coleta, para o modelo não inventar endereço de terceiro.
No `RESOLVER` com compromisso (RFC-0009), o prompt leva o "agora" no fuso do tenant e o modelo
devolve `lembreteQuando` em ISO-8601 **só quando consegue resolver a data**; sem ela (ou se a data
não sobrevive à validação), vem `lembreteTexto` com a frase perguntando — o app espera a resposta e
não despacha nada.

`POST /v1/consulta` (020): pergunta livre → template de whitelist → SQL determinístico
(INV-10/INV-15). Resposta `{pergunta, template, resposta, itens:[{id,titulo,classe,valorEmJogo}]}`;
fora da whitelist, template `DESCONHECIDO` ("não sei responder isso sobre a fila").

`POST /v1/comandos` (021): lote **idempotente** por `(org_id, comandoId)` — reenvio devolve o
resultado gravado, não re-executa (INV-13). Cada `Comando{comandoId, intencao, pendenciaId?, campos}`
mapeia para a ação determinística existente (INV-10); intenções: `RESOLVER, REPASSAR, RESERVAR,
REPOUSAR, ADIAR, REGISTRAR, CONSULTAR`. Resposta `{resultados:[{comandoId, status:
OK|IGNORADO|RECUSADO|ERRO, detalhe?, pendenciaId?, consulta?}]}`. Pendência que já saiu da fila →
`IGNORADO` (não erro); `REGISTRAR` (escape) nunca é ignorado; `CONSULTAR` traz `consulta`.

`RESOLVER` aceita `campos.lembrete {quando, texto}` (RFC-0009): fecha o item e guarda o compromisso
que sobrou, na mesma transação do comando — reenviar o mesmo `comandoId` não duplica o lembrete.
`quando` é ISO-8601 com fuso; data malformada, no passado ou a mais de 12 meses vira `ERRO` do
comando (o item **não** fecha), nunca silêncio.

`REPASSAR` (RFC-0008) tem **um** destino: `campos.dono` (pessoa interna) **ou** `campos.contato`
(externo) — os dois juntos, ou nenhum, dão `ERRO`. O contato vem como `{id}` (já registrado) ou
`{nome, canal, endereco}` (registrado na hora, na mesma transação do comando — logo o reenvio do
mesmo `comandoId` não cria contato duplicado). `id` de outra organização → `ERRO "contato não
encontrado"` (INV-15). Destino externo enfileira o `AVISO_REPASSE`; em trajeto ele nasce represado
e só sai na confirmação do resumo (INV-14).

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

### Contatos externos de repasse (pacote 025, RFC-0008)
```
POST   /v1/contatos                        { nome, canal, endereco }   # registra destinatário externo
GET    /v1/contatos                        # contatos do tenant
PUT    /v1/contatos/{id}                   { nome, canal, endereco }   # o telefone mudou; o contato é o mesmo
```

`canal`: `WHATSAPP` | `EMAIL`; `endereco` é telefone E.164 ou e-mail. `POST` → `201 {id}`;
`400 alcada:contato.invalido` se falta nome/canal/endereco, `422 alcada:contato.invalido` se o canal
não é um dos dois. `GET` → `[{id, nome, canal, endereco}]`. `PUT` → `204`; `404
alcada:contato.inexistente` quando o contato não é do tenant (INV-15) — as delegações que apontam
para o contato seguem válidas (não há exclusão).
Contato é **dado operacional de repasse, não conta** (INV-02): serve para delegar a quem não é
usuário do Alçada, e o repasse o avisa pelo canal (`AVISO_REPASSE` no outbox). `endereco` é PII
(ADR-0011): não trafega pelo gateway de modelos.

### Destino reconhecível de repasse (pacote 029)
```
GET  /v1/destinos-repasse?busca=&classe=&limite=
POST /v1/pendencias/{id}/repassar
```

`GET` unifica pessoas internas e contatos externos, no máximo 8, com endereço externo mascarado:
`[{tipo,id,nome,detalhe,canal,recente,usadoNaClasse,nivelSugerido,prazoSugerido}]`. Busca vazia
devolve recentes/candidatos limitados, nunca um dump irrestrito do diretório.

O `POST` aceita `{destino:{tipo:'INTERNO',pessoaId}|{tipo:'EXTERNO',contatoId}|
{tipo:'EXTERNO_NOVO',nome,canal,endereco},nivel,prazo}`. Contato equivalente é reutilizado; contato
novo, delegação, trilha e outbox participam da mesma transação. `/delegar` com `{donoId,nivel,prazo}`
permanece como contrato compatível para clientes existentes.

### Evidências do piloto (pacote 028, acesso ADMIN)
```
GET  /v1/piloto/relatorio?inicio=&fim=
POST /v1/piloto/reconciliacoes                 {semana,decisoesForaDaFila,observacao?}
GET  /v1/piloto/descartes/amostra?inicio=&fim=&limite=&semente=
POST /v1/piloto/descartes/{id}/avaliacoes     {resultado}
```

O relatório separa propostas, ausência, intervenções, devoluções, escaladas e reversões; apresenta
escape como piso de misses conhecidos, nunca como recall. A amostra só inclui bruto ainda retido.
Em dev/demo, `X-Alcada-Papel: ADMIN` substitui o papel do token; em produção o header não concede
acesso. A rota web `/piloto` não integra a navegação diária.

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

### Consulta completa de itens (P033)
```
GET    /v1/itens?q=&status=&classe=&nivel=&pessoaId=&de=&ate=&origem=&pagina=&tamanho=
GET    /v1/itens/{id}
```

`GET /v1/itens` é um read model histórico paginado, não uma fila operacional. `pagina` começa em
zero e `tamanho` aceita de 1 a 100. Os filtros fechados são estado, classe, nível da última
Delegação, pessoa, período de criação e origem reconhecível. A busca textual lê apenas campos
estruturados e `documento_indice`; nunca pesquisa o bruto retido. O retorno contém estado,
executor/origem quando disponíveis, atividade mais recente, quantidade de eventos e links
situados. `GET /v1/itens/{id}` acrescenta a trilha completa. Ambos exigem tenant resolvido e um id
de outra organização responde `404`.

### Captura
```
POST   /v1/captura/eventos                 # webhook autenticado por fonte
POST   /v1/captura/linktor                 # webhook do Linktor (HMAC por fonte, ADR-0021/0025)
POST   /v1/captura/audio                   # texto transcrito no dispositivo + metadados
GET    /v1/fontes
POST   /v1/fontes                          # declaração de canal (ADR-0011)
POST   /v1/fontes/{id}/desativar
PUT    /v1/fontes/{id}/canal               { linktorChannelId }   # canal de saída (RFC-0008)
```

No retorno correlacionado (P030), o aviso direto de WhatsApp leva o token opaco em
`metadata.alcada_correlation`; a volta esperada é `data.context.alcada_correlation`. Depois da
validação HMAC, tenant, canal, autor, expiração e revogação, a resposta é registrada minimizada
como `OBSERVADO` e não cria outra pendência. Token ausente/inválido ou autor divergente segue a
captura normal, sem correlação heurística. O contrato de ida e volta ainda exige validação na
Linktor antes de habilitar efeitos operacionais.
`GET /v1/fontes` → `[{id, tipo, identificador, ativa, linktorChannelId}]`. `PUT .../canal` define
por onde o **aviso de repasse** sai no WhatsApp (o despachante usa a primeira fonte `WHATSAPP`
**ativa** com canal); string vazia limpa. `204`; `404 alcada:fonte.inexistente` fora do tenant (INV-15).
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
PROTECAO_AGENDA            # reserva trimestral após janela de reversibilidade
```

## Contratos de erro relevantes
| Código | Situação |
|---|---|
| `409 pendencia.estado_invalido` | transição não permitida pela máquina de estados |
| `409 janela.expirada` | desfazer fora da janela de reversibilidade |
| `422 alcada.inelegivel` | classe não elegível ao nível solicitado (ADR-0004) |
| `423 movimento.bloqueado` | ação recusada em trajeto (ADR-0014) |
