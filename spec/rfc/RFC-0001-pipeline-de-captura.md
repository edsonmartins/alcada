# RFC-0001 — Pipeline de captura e extração

**Status:** proposto · **Implementa:** ADR-0005, ADR-0006, ADR-0007, ADR-0011

## Objetivo
Transformar eventos brutos de canais heterogêneos em pendências estruturadas, sem intervenção do
gestor, com recall mensurável e deduplicação.

## Fluxo

```
Fonte ──► Ingestor ──► Normalizador ──► Filtro de relevância ──► Extrator ──► Resolvedor
                                              │                                    │
                                              └─► descarte (métrica)               ▼
                                                                              Deduplicador
                                                                                   │
                                                              ┌────────────────────┴───┐
                                                     item novo│                        │fusão
                                                              ▼                        ▼
                                                         Classificador          temperatura++
                                                              │                  + resposta no canal
                                                              ▼
                                                          Roteador
                                              ┌───────────────┼───────────────┐
                                              ▼               ▼               ▼
                                          ENTRADA        N1 automático     ESTEIRA
```

## Componentes

**Ingestor** — duas origens apenas (ADR-0021):

1. **Linktor** para todo canal de mensagem — WhatsApp e e-mail. A Alçada não fala com mensageria nem
   com servidor de e-mail diretamente. Recebe o envelope `MensagemRecebida` já normalizado, com o
   trecho novo da thread isolado, e referencia o bruto por `mensagem_id` sem duplicá-lo.
2. **Webhook próprio** para sistema — monitoramento, ERP, esteira — e áudio transcrito no dispositivo.

Cada evento carrega `fonte_id` declarada (ADR-0011). Entrega no outbox de ingestão; nada é processado
no thread do webhook.

**Normalizador** — formato canônico: `{fonte, autor, timestamp, texto, anexos, thread_ref, entidades_citadas}`.
Para e-mail, resolve thread e extrai **apenas o trecho novo**; a pendência costuma estar no quarto
parágrafo da nona mensagem.

**Filtro de relevância** — barra determinística antes do modelo: menção ao bot, resposta a item
existente, padrão configurado, ou remetente/canal marcado como sempre-relevante. Varredura completa
é proibida. Taxa de descarte é métrica de saúde.

**Minimizador** — antes de qualquer chamada ao gateway de modelo: pseudonimiza nomes de pessoa e
empresa por token, remove identificadores (CPF/CNPJ, telefone, e-mail, chaves), trunca ao trecho
relevante. A re-hidratação acontece localmente sobre a resposta (ADR-0020 §3).

**Extrator** — via `ModelGateway` (RFC-0007), com schema estrito e `require_parameters: true`.
Saída estruturada validada por schema:
```json
{ "titulo": "", "quem_espera": "", "o_que_trava": "",
  "prazo_implicito": null, "valor_em_jogo": null,
  "entidades": [], "classe_sugerida": "DECISAO|BLOQUEIO|ESTEIRA",
  "confianca": 0.0 }
```
Saída fora do schema é rejeitada e reprocessada uma vez; persistindo, vira item de baixa confiança
com aviso na triagem.

**Resolvedor de entidade** — casa citações informais ("o Panorama", "aquele do Vila Nova") com
entidades do tenant. Índice de apelidos alimentado por confirmações do gestor.

**Deduplicador** — chave: `entidade + janela(7d) + similaridade(embedding) > limiar`. Empate resolve
por thread. Fusão registra trilha e é reversível em 1 toque.

**Classificador + Roteador** — classe (ADR-0006) e regra de autonomia vigente decidem o destino. O
roteamento é determinístico (INV-10): o modelo entrega classe e confiança; a regra decide.

## Contratos

`POST /v1/captura/eventos` — ingestão genérica (webhook autenticado por fonte)
`POST /v1/captura/audio` — upload de áudio já transcrito no dispositivo (ADR-0014)
Evento emitido: `pendencia.criada`, `pendencia.fundida`, `evento.descartado`

## Métricas obrigatórias
- **recall** contra baseline manual (F1: amostragem semanal auditada por humano)
- precisão da classe (falso BLOQUEIO é grave)
- taxa de fusão e taxa de reversão de fusão
- latência ingestão → item visível (alvo: < 30 s)

## Riscos
| Risco | Mitigação |
|---|---|
| Recall baixo mata a proposta de valor | medição semanal desde F1; escape de captura manual monitorado |
| Falso positivo polui a fila | descarte de 1 toque realimenta o filtro |
| Fusão errada esconde pendência | reversão simples + log; limiar conservador |
