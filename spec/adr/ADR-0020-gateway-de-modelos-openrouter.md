# ADR-0020 — OpenRouter como gateway de modelos; emenda ao INV-12

**Status:** aceito · **Data:** 2026-07 · **Emenda:** INV-12 · **Supersede parcialmente:** ADR-0010
**Criticidade:** alta — toca o diferencial declarado do produto

## Contexto

ADR-0010 e INV-12 estabeleceram inferência local para todo conteúdo sensível. A decisão de produto
agora é usar **OpenRouter** como provedor de LLM. Isso não é compatível com a redação original do
INV-12, e a constituição exige emenda explícita em vez de contradição silenciosa.

Motivação legítima da mudança: operar stack de inferência própria é custo fixo e complexidade de
deploy numa fase em que o produto ainda muda de forma. Acesso imediato a modelos de fronteira melhora
extração, redação e classificação — que são o coração do valor percebido.

## O que muda no modelo de exposição

Toda requisição passa a cruzar **duas fronteiras administrativas**: aplicação → OpenRouter e
OpenRouter → provedor downstream. <cite index="6-1">O OpenRouter vê o prompt para rotear e registrar uso; o provedor downstream recebe o prompt para gerar a resposta; as políticas de retenção e treinamento aplicáveis são a união das políticas das duas partes</cite>.

Consequência para LGPD: a cadeia de suboperadores precisa ser enumerável. Roteamento dinâmico não é.

## Decisão

### 1. Emenda ao INV-12
O invariante passa a ler-se: *conteúdo sensível é processado sob controle de retenção verificável;
inferência local permanece obrigatória no SKU on-premise e para as classes listadas em (4).*
A obrigatoriedade universal de inferência local é revogada. A minimização de dados **não** é.

### 2. Guardrails obrigatórios em toda chamada
Configuração fixa no gateway, não opcional por chamador:

```json
"provider": {
  "only": ["<lista fixa de provedores homologados>"],
  "allow_fallbacks": false,
  "data_collection": "deny",
  "zdr": true,
  "require_parameters": true
}
```

- <cite index="3-1">`order`/`only` controla quais provedores podem atender, `data_collection: deny` bloqueia provedores que armazenam ou treinam com os dados, `zdr: true` exige endpoints de retenção zero, e `allow_fallbacks: false` faz o OpenRouter retornar erro em vez de rotear para fora da lista</cite>.
- `allow_fallbacks: false` é o que torna a lista de suboperadores **enumerável** para o RIPD. Falha vira erro tratado, não roteamento silencioso.
- `require_parameters: true` é obrigatório porque o extrator depende de schema estrito: <cite index="10-1">sem ele, provedores sem suporte a `json_schema` podem receber a requisição e o retorno cair para `json_object`</cite>. Isso quebraria a validação do RFC-0001.
- <cite index="2-1">O ZDR pode ser exigido globalmente, por grupo de modelos, por guardrail ou por requisição</cite>. Usar os três níveis: conta, guardrail e parâmetro por requisição.

### 3. Minimização antes da chamada
Como o Linktor (ADR-0021) mantém a mensagem bruta na infraestrutura própria, o que sai é apenas o
texto necessário à extração. Antes de qualquer chamada:

- pseudonimização de nomes de pessoas e razões sociais por token (`PESSOA_1`, `EMPRESA_2`), com
  re-hidratação local após a resposta
- remoção de CPF/CNPJ, telefone, e-mail, chaves de acesso e anexos
- truncamento ao trecho relevante — nunca a thread inteira

Isso reduz a exposição real muito mais do que qualquer cláusula contratual.

### 4. Classes que não saem
Permanecem em inferência local, sem exceção e sem opt-in:

- áudio e transcrição do gestor (ADR-0014)
- conteúdo de contrato e valores de negociação
- avaliação de parceiro e conteúdo do dossiê de decisão
- qualquer tenant com SKU on-premise

### 5. Porta de abstração
`ModelGateway` como porta (ADR-0015). OpenRouter é adaptador. Trocar provedor ou apontar para stack
local não pode exigir mudança em módulo de domínio. Ver RFC-0007.

## Impacto no posicionamento comercial

**Isto precisa ser resolvido antes da primeira venda.** Não é possível sustentar "soberania de dados"
como diferencial e rotear conteúdo para provedores fora do país. Duas ofertas explícitas:

| SKU | Inferência | Público |
|---|---|---|
| **Alçada Cloud** | OpenRouter com guardrails desta ADR | PME, ciclo curto |
| **Alçada Soberano** | inferência local, sem saída de conteúdo | cliente regulado ou com exigência de residência |

<cite index="3-1">Para contas enterprise existe roteamento in-region na União Europeia via `eu.openrouter.ai`</cite> — não há equivalente para o Brasil, então residência nacional só é atendida pelo SKU Soberano.

## Consequências
- (+) Time-to-market muito menor; sem operar GPU nas fases F1–F4.
- (+) Qualidade superior em redação e dossiê, que é onde o usuário percebe valor.
- (−) Perde-se o argumento de soberania no SKU Cloud; exige segmentação de oferta.
- (−) Dependência de disponibilidade e preço de terceiros; `allow_fallbacks: false` converte
  indisponibilidade em erro, que precisa de fila e retentativa.
- (−) Cadeia de suboperadores precisa constar no contrato e no RIPD, e ser revisada a cada mudança
  na lista `only`.

## Pontos de atenção verificados
- <cite index="2-1">A exigência de ZDR se aplica ao roteamento de inferência; não se aplica a plugins e ferramentas como busca web, que têm políticas próprias</cite>. Nenhum plugin do OpenRouter deve ser habilitado neste produto.
- <cite index="5-1">Cache em memória é considerado compatível com ZDR; para não-retenção completa é necessário o filtro de "No Caching"</cite>. Avaliar por classe de conteúdo.
- <cite index="2-1">Existem endpoints que não treinam com os dados mas os retêm, por varredura de abuso ou razão legal</cite> — daí `data_collection: deny` **e** `zdr: true`, não um ou outro.

## Emenda (piloto, 2026-07)
A regra original "transporte real do OpenRouter só em `prod`" acoplava o gateway real ao OIDC
obrigatório do `prod`, impedindo o piloto (profile `demo`, sem IdP) de usar IA. Emenda: o transporte
real passa a ser ligado por **`gateway.openrouter.enabled`** (`@IfBuildProperty`), ativo em `prod` **e**
`demo`. Garantias preservadas: a chamada real ainda depende da chave (`OPENROUTER_API_KEY`); sem ela,
degrada como o stub; minimização e roteamento por sensibilidade continuam (RESTRITA nunca sai, vai
para inferência local); o Linktor **permanece stub** fora de `prod`. Dev/test seguem no stub. Habilita,
no piloto, interpretação/consulta por LLM e (fase 2) STT/TTS de áudio do OpenRouter.

## Revisão
Reavaliar em 12 meses ou quando: houver exigência de residência nacional em contrato relevante, o
custo por item ultrapassar o de operar inferência própria, ou o volume justificar o custo fixo.
