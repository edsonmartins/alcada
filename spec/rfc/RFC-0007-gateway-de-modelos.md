# RFC-0007 — Gateway de modelos

**Status:** proposto · **Implementa:** ADR-0020, ADR-0009, ADR-0010 (parcial)

## Objetivo
Uma porta única para inferência, com política aplicada no gateway e não no chamador. Nenhum módulo de
domínio conhece OpenRouter, provedor ou modelo.

## Porta

```java
public interface ModelGateway {
    <T> Extracao<T> extrair(TarefaExtracao<T> tarefa);   // schema estrito obrigatório
    Redacao redigir(TarefaRedacao tarefa);
    Classificacao classificar(TarefaClassificacao tarefa);
    Embedding embutir(TarefaEmbedding tarefa);
}
```

Toda tarefa carrega `Sensibilidade { PUBLICA, INTERNA, RESTRITA }`. O chamador declara a
sensibilidade; **o gateway decide o destino**. Chamador não escolhe provedor nem modelo.

## Fronteira do minimizador (fechado na revisão de sessão 1)

`captura.extracao` **não chama o gateway diretamente**. A cadeia é:

```
captura.extracao ─► Minimizador ─► ModelGateway ─► (provedor) ─► Re-hidratador ─► extração
                        │                                             ▲
                        └────── mapa pseudônimo→real (em memória) ────┘
```

- O **Minimizador** vive em `captura`, como porta consumida pela extração. O gateway nunca recebe
  texto não minimizado quando a sensibilidade é `INTERNA`.
- O **mapa de pseudônimos é efêmero e por chamada**: existe em memória durante a requisição e é
  descartado após a re-hidratação. **Não é persistido.** Persistir mapeamento pseudônimo→real criaria
  um novo conjunto de dado sensível, exatamente o que a minimização evita.
- Resolução de entidade (`captura.entidades`) opera **depois**, sobre o texto re-hidratado, local.
- O bruto permanece no Linktor (ADR-0021) e não trafega.

**Confirmação sobre INV-10:** extração é caminho de **proposta**, não de execução. O modelo devolve
campos e confiança; quem decide roteamento, alçada e efeito externo é regra determinística. Não há
violação. A vedação do INV-10 alcança o caminho de execução de efeito externo, não o de interpretação.

## Roteamento por sensibilidade

| Sensibilidade | Conteúdo típico | Destino |
|---|---|---|
| `PUBLICA` | classificação de tipo, sumarização de texto já público | OpenRouter |
| `INTERNA` | extração de pendência a partir de mensagem minimizada | OpenRouter, após minimizador |
| `RESTRITA` | áudio do gestor, valores de contrato, dossiê, avaliação de parceiro | **local, sempre** |

Tenant com SKU Soberano força tudo para local, independentemente da classificação.

## Política fixa do adaptador OpenRouter

Aplicada pelo gateway em toda requisição; não é parametrizável pelo chamador:

```json
{
  "provider": {
    "only": ["<homologados>"],
    "allow_fallbacks": false,
    "data_collection": "deny",
    "zdr": true,
    "require_parameters": true
  }
}
```

Reforço em três níveis (conta, guardrail, requisição), porque a exigência de ZDR por requisição opera
como "OU" com as configurações de conta e de guardrail.

**Plugins e ferramentas do OpenRouter ficam desabilitados.** A garantia de retenção zero cobre o
roteamento de inferência, não plugins como busca web, que têm política própria.

**Cache:** avaliar por classe. Cache em memória é tratado como compatível com ZDR pelo roteador; onde
for exigida não-retenção completa, aplicar o filtro de "sem cache".

## Falha e disponibilidade

Com `allow_fallbacks: false`, indisponibilidade vira erro em vez de roteamento fora da lista. O
gateway trata:

1. retentativa com backoff dentro da lista homologada
2. na exaustão, **enfileira a tarefa** — captura nunca é perdida; o item entra com
   `confianca = null` e aviso de "extração pendente" na triagem
3. tarefa de redação falha de forma visível ao usuário, sem degradar para modelo não homologado

## Homologação de provedor
Entrar na lista `only` exige: suporte a `json_schema` estrito, endpoint marcado como retenção zero,
coleta de dados negada, e registro no anexo de suboperadores do contrato e do RIPD. **Mudança na
lista é mudança contratual**, não configuração de infraestrutura.

## Observabilidade
Por chamada: tarefa, sensibilidade, destino (provedor efetivo), modelo, tokens, latência, custo,
resultado da validação de schema. Sem prompt e sem resposta em log — `mensagem_id` como referência.

Alertas: taxa de falha de schema acima do limiar; roteamento recusado por guardrail; custo por item
acima do orçado.

## Custo
Orçamento por item capturado e por bloco de decisão, com teto mensal por tenant. Extração é volume
alto e modelo pequeno; redação é volume baixo e modelo caro. Medir separado desde o primeiro dia — é
o número que decide, no futuro, se vale internalizar a inferência (ADR-0020, revisão).

## Testes
- schema estrito rejeitado por provedor sem suporte → deve falhar, nunca cair para `json_object`
- guardrail negando provedor fora da lista
- minimizador: nenhum identificador direto atravessa a fronteira (teste de vazamento com corpus real)
- re-hidratação: tokens voltam corretamente e não vazam entre itens
- tenant Soberano nunca sai
