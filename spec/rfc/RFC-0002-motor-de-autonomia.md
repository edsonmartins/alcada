# RFC-0002 — Motor de autonomia (N1/N2/N3)

**Status:** proposto · **Implementa:** ADR-0003, ADR-0004, ADR-0016

## Objetivo
Executar o contrato de delegação com garantias de prazo, escalonamento, reversibilidade e trilha.

## Máquina da delegação N2

```
DELEGADA ──(prazo vence)──► AGUARDANDO_JANELA ──(janela fecha)──► EXECUTADA
    │                              │
    │                              └──(gestor intervém)──► DEVOLVIDA (ENTRADA)
    ├──(executor propõe e conclui)────────────────────────► EXECUTADA
    ├──(gestor intervém antes)───────────────────────────► DEVOLVIDA
    └──(silêncio de ambos até prazo+X)──────────────────► ESCALADA (ENTRADA, marcada)
```

**Silêncio de ambos** é caso distinto e deliberado: se o executor não propôs nada e o gestor não
respondeu, o item **não executa em branco**. Sobe para o gestor sinalizado como "ninguém agiu".

## Parâmetros por classe de decisão

```yaml
classe: desconto_comercial
  nivel_padrao: N2
  valor_limite: 5000.00
  janela_reversibilidade: PT4H
  escalonamento: PT24H
  elegivel_n1: true
classe: rescisao_contrato
  nivel_maximo: N3        # inelegível a N2 por padrão (ADR-0004)
```

## Agendamento
Scheduler persistente (tabela de jobs + worker), nunca timer em memória. Reprocessamento após
restart é garantido.

### Chave de idempotência (fechado na revisão de sessão 1)

`ocorrencia` era indefinido. Substituído por regra explícita:

| Caso | Chave |
|---|---|
| Job do ciclo de autonomia (vencimento, janela, escalonamento) | `(delegacao_id, transicao)` |
| Job sem sub-agregado próprio (despertar de `DORMINDO`) | `(pendencia_id, transicao, ocorrencia)` |

Onde `ocorrencia` é o **contador do adormecimento** — incrementado a cada vez que a pendência entra
em `DORMINDO`. Uma pendência pode adormecer várias vezes; cada uma agenda seu próprio despertar.

Para autonomia a chave não precisa de contador: redelegar cria uma `Delegacao` nova, com id novo.
Isso elimina a colisão que existiria se a chave fosse por pendência.

### Parâmetros de tempo

Configuráveis por `classe_decisao` dentro do tenant. Defaults quando não especificado:

| Parâmetro | Default | Significado |
|---|---|---|
| `janela_reversibilidade` | `PT4H` | após o prazo, antes de qualquer efeito externo |
| `escalonamento` | `PT24H` | contado **a partir do prazo**, e só se não houver `PROPOSTA_REGISTRADA` |
| `lembrete_executor` | 50% do prazo | — |
| `lembrete_gestor` | 90% do prazo | — |

Escalonamento **não** se aplica quando o executor propôs e o gestor silenciou — esse é o caminho
normal do N2, que executa. Escalonamento existe apenas para o silêncio de ambos.

## Efeito externo
Toda notificação e execução passa por **outbox transacional**. O efeito externo só é emitido após
`janela_reversibilidade` encerrar — isso vale também para o modo trajeto (ADR-0014), cuja janela
fecha ao fim do deslocamento.

## Modo ausência
Gestor marcado como ausente (férias, licença) converte automaticamente novas delegações N2 em N3 e
estende janelas em aberto, com aviso ao executor. Sem isso, ausência vira execução silenciosa em
massa.

## Trilha (formato)
```json
{ "tipo": "EXECUTADA_POR_AUSENCIA",
  "ator": "SISTEMA:motor-autonomia",
  "prazo": "2026-07-23T18:00:00-03:00",
  "proposta": "reajuste de 4,2% conforme índice contratual",
  "intervencoes": [],
  "janela": "PT4H" }
```

## API
```
POST   /v1/pendencias/{id}/delegar      { dono, nivel, prazo }
POST   /v1/pendencias/{id}/intervir     -> devolve para ENTRADA
POST   /v1/pendencias/{id}/promover     { nivel: N1 }  -> propõe regra
GET    /v1/delegacoes?dono=&status=     -> superfície do executor
```

## Riscos
| Risco | Mitigação |
|---|---|
| Execução indesejada com gestor ausente | modo ausência automático |
| Executor não vê a delegação a tempo | notificação no canal onde ele já vive + lembrete a 50% do prazo |
| Relógio/timezone | tudo em UTC, apresentação em `America/Sao_Paulo`, prazos ancorados em horário comercial |
