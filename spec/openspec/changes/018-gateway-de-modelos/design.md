# Design — 018 gateway de modelos

Referências: `adr/ADR-0020-gateway-de-modelos-openrouter.md` e `rfc/RFC-0007-gateway-de-modelos.md`.

## Módulos
`plataforma.gateway` — porta `ModelGateway` + adaptador OpenRouter + adaptador local (stub). Consumido
por `captura` (extração/classificação) e, depois, por `assistente` (redação/dossiê). Nenhum módulo de
domínio conhece o provedor.

## Porta
```java
interface ModelGateway {
  <T> Extracao<T> extrair(TarefaExtracao<T> tarefa);   // schema estrito obrigatório
  Redacao        redigir(TarefaRedacao tarefa);
  Classificacao  classificar(TarefaClassificacao tarefa);
  Embedding      embutir(TarefaEmbedding tarefa);
}
enum Sensibilidade { PUBLICA, INTERNA, RESTRITA }
```
O chamador declara `Sensibilidade`; **o gateway decide o destino**. Chamador nunca escolhe provedor
nem modelo.

## Roteamento por sensibilidade
| Sensibilidade | Destino |
|---|---|
| `PUBLICA` | OpenRouter |
| `INTERNA` | OpenRouter, **após** o minimizador |
| `RESTRITA` | local, sempre |

Tenant com SKU **Soberano** força tudo para local, independentemente da classificação.

## Fronteira do minimizador (RFC-0007)
```
captura.extracao ─► Minimizador ─► ModelGateway ─► (provedor) ─► Re-hidratador ─► extração
                        │                                             ▲
                        └────── mapa pseudônimo→real (em memória) ────┘
```
- O gateway **nunca** recebe texto não minimizado quando `INTERNA`.
- O mapa pseudônimo→real é **efêmero, por chamada, em memória** — nunca persistido.
- Re-hidratação é local e por item; um item nunca vê o token de outro.

## Política fixa do adaptador OpenRouter (não parametrizável pelo chamador)
```json
{ "provider": {
    "only": ["<homologados — config, começa com um>"],
    "allow_fallbacks": false,
    "data_collection": "deny",
    "zdr": true,
    "require_parameters": true } }
```
Reforço em três níveis (conta, guardrail, requisição). **Plugins/ferramentas do OpenRouter
desabilitados** (a garantia ZDR cobre inferência, não plugins). Cache avaliado por classe.

## Tabelas
```sql
tarefa_reprocesso(id, org_id, tipo_tarefa, ref_mensagem_id, tentativas,
                  disponivel_em, status)          -- NUNCA guarda texto sensível; só referência
chamada_modelo(id, org_id, tarefa, sensibilidade, provedor_efetivo, modelo,
               tokens_in, tokens_out, latencia_ms, custo, schema_ok, ref_mensagem_id, ocorrido_em)
```
`tarefa_reprocesso` referencia a mensagem (bruto no Linktor); ao reprocessar, a minimização roda de
novo — nada sensível é persistido na fila. `chamada_modelo` **não** guarda prompt nem resposta.

## Falha e disponibilidade
Com `allow_fallbacks:false`, indisponibilidade vira erro, não roteamento fora da lista. O gateway:
1. retenta com backoff **dentro** da lista homologada;
2. na exaustão, enfileira em `tarefa_reprocesso`; o item de captura entra com `confianca = null` e
   aviso de "extração pendente" na triagem — captura nunca perdida;
3. tarefa de **redação** falha de forma visível ao usuário, sem degradar para modelo não homologado.

## Homologação de provedor
A lista `only` é **configuração**, começando com um provedor fixado; o segundo é config, não código.
Entrar na lista exige `json_schema` estrito, endpoint ZDR, coleta negada e registro no anexo de
suboperadores do contrato/RIPD. **Mudança na lista é evento contratual auditado** (registra versão).

## Observabilidade
Por chamada: tarefa, sensibilidade, provedor efetivo, modelo, tokens, latência, custo, resultado da
validação de schema. Sem prompt e sem resposta — `mensagem_id` como referência. Custo de extração e de
redação medidos **separadamente** desde o primeiro dia (decide, no futuro, internalizar inferência).

## Riscos técnicos
Reflexão é o inimigo do native image: **cliente HTTP simples com serialização explícita**, não SDK
pesado (CLAUDE.md §4). Dependência de disponibilidade de terceiro exige fila e retentativa robustas.
