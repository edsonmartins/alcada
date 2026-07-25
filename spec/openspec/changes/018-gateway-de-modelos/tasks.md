# Tasks — 018 gateway de modelos

## Integração real (verificado no PASSO ZERO — OpenRouter)
- [x] Provider(es) do `only` (RIPD): **`deepinfra`** — Opção C, um único suboperador ZDR
- [x] Extração: **`google/gemma-3-12b-it`** (`structured_outputs` estrito, ZDR, $0,05/$0,15 por 1M)
- [x] Redação: **`meta-llama/llama-3.3-70b-instruct`** (ZDR, $0,72/$0,72 por 1M)
- [x] Transporte real (`TransporteHttp`) **só em `prod`**; `TransporteStub` fora de prod (nunca bate no host)
- [x] Fiação vs dado real explícito (config + design): `:free` só para fiação sintética; dado real só nos endpoints pagos ZDR
- [x] Chave de API só por env (`OPENROUTER_API_KEY`) — nunca hardcoded/log/commit/trilha

## Porta e roteamento
- [x] Módulo `plataforma.gateway`; porta `ModelGateway` (extrair, redigir, classificar, embutir)
- [x] `Sensibilidade { PUBLICA, INTERNA, RESTRITA }` e roteamento por sensibilidade
- [x] Forçamento local para SKU Soberano e para classe `RESTRITA` (fail-closed se SKU desconhecido)

## Adaptador OpenRouter
- [x] Cliente HTTP simples com serialização explícita (sem SDK pesado, native-safe) atrás do seam `TransporteModelo`
- [x] Política fixa não parametrizável: `only`, `allow_fallbacks:false`, `data_collection:deny`, `zdr:true`, `require_parameters:true`
- [x] Plugins/ferramentas desabilitados (array `plugins` vazio no corpo)
- [x] `only` como configuração parametrizada, começando com um provedor
- [x] Extração com `json_schema` estrito; recusa quando o provedor não suporta (nunca `json_object`)
- [ ] Reforço ZDR em três níveis (conta/guardrail/requisição) — nível de requisição feito; conta/guardrail são configuração no painel OpenRouter (fora do código)

## Fronteira do minimizador
- [x] `Minimizador`/`Minimizacao` em `captura`: pseudonimiza pessoas/empresas, remove CPF/CNPJ/telefone/e-mail
- [x] Re-hidratação local por item; mapa efêmero em memória, nunca persistido

## Falha e reprocesso
- [x] Retentativa com backoff dentro da lista homologada
- [x] `tarefa_reprocesso` (só referência, sem texto sensível) + `WorkerReprocesso`
- [x] Item de extração com `confianca = null` (pendente) na indisponibilidade
- [x] Redação falha visível (Indisponível propaga), sem degradar; sem enfileirar
- [x] Porta `ReprocessadorExtracao` (implementada por captura/001)

## Adaptador local (SKU Soberano)
- [x] Stub da porta local — garante que RESTRITA/Soberano não saem; implementação real quando o SKU entrar

## Observabilidade e custo
- [x] `chamada_modelo` sem prompt/resposta; `mensagem_id` como referência
- [x] Colunas de custo e tokens (extração vs redação medidas separadamente por `tarefa`)
- [ ] Alertas (falha de schema/guardrail/custo) — expostos como métricas no pacote 009 (radar-e-metricas)
- [ ] Registro auditável de versão da lista `only` — a lista é config; versionamento contratual é processo externo

## Testes
- [x] Provedor sem `json_schema` → falha, nunca `json_object`
- [x] Indisponibilidade → reprocesso, captura não perdida
- [x] Tenant Soberano nunca sai; classe `RESTRITA` sempre local (transporte externo nunca chamado)
- [x] Vazamento: nenhum identificador direto atravessa a fronteira (corpus)
- [x] Re-hidratação não vaza token entre itens
- [x] Política fixa presente (`only=[deepinfra]`) e não sobrescrita
- [x] Guardrail nega provider fora do `only` → erro tratado, não degrada nem enfileira
- [x] Log sem prompt nem resposta

---
**Estado:** integração real ligada — provider único `deepinfra` (ZDR), extração `gemma-3-12b-it`,
redação `llama-3.3-70b-instruct`. Transporte real só em prod; 88 testes JVM; nativo ~69 MB RSS.
Falta apenas exercitar contra o host real (fiação `:free` sintética) — passo manual de deploy.
