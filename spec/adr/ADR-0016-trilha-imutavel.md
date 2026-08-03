# ADR-0016 — Trilha imutável como prova

**Status:** aceito · **Data:** 2026-07 · **Relacionado:** INV-11, ADR-0004

## Contexto
Execução por ausência (N2) e promoção de autonomia mudam quem responde por uma decisão. Sem prova
verificável, o mecanismo não sobrevive à primeira decisão controversa.

## Decisão
Registro **append-only** por pendência, com:

- ator (`HUMANO:{id}` | `SISTEMA:{regra}` | `ASSISTENTE:{modelo,versão}`)
- tipo de evento, timestamp, origem (canal, fonte, mensagem)
- estado anterior e posterior
- para execução por ausência: prazo, proposta, ausência de resposta, janela decorrida
- para assistente: sugestão emitida e **desfecho** (aceita, recusada, ignorada)

Sem update nem delete. Correção é novo evento de compensação. Direito de eliminação (LGPD) opera por
pseudonimização do titular, preservando a cadeia (ADR-0011).

## Consequências
- (+) Viabiliza a conversa jurídica sobre N2 e a defesa de alçada em auditoria.
- (+) Recusa de sugestão vira insumo de aprendizado de autonomia.
- (−) Volume de escrita e custo de armazenamento; particionamento e arquivamento frio obrigatórios.


---

## Anexo — vocabulário fechado (normativo)

Fixado na revisão de sessão 1. Nenhum módulo pode gravar tipo fora desta lista sem emenda a este ADR.

### Formato do ator
```
HUMANO:{pessoa_id}
SISTEMA:{motor|regra}:{identificador}      ex.: SISTEMA:regra:desconto_ate_5
ASSISTENTE:{modelo}@{versao}               ex.: ASSISTENTE:extrator@2026-07-01
```

### Tipos de evento

**Captura**
`CAPTADA` · `FUNDIDA` · `DESFUNDIDA` · `ROTEADA_POR_REGRA`

Descarte por irrelevância **não gera trilha** — não existe pendência ainda. Vai para métrica de
captura.

**Triagem**
`RESOLVIDA` · `REPASSADA` · `RESERVADA` · `REPOUSADA` · `ADIADA` · `DESPERTADA` · `DESCARTADA`

**Lembrete e compromisso** *(acrescentado por RFC-0009)*
`LEMBRETE_CRIADO` (na pendência de **origem**, aponta para o lembrete que ficou) ·
`COMPROMISSO_AGENDADO` · `FALHA_COMPROMISSO` (entrega no calendário do gestor)

**Autonomia**
`PROPOSTA_REGISTRADA` · `JANELA_INICIADA` · `EXECUTADA` · `EXECUTADA_POR_AUSENCIA` ·
`DESFEITA_NA_JANELA` · `INTERROMPIDA` · `ESCALADA` · `CONVERTIDA_POR_AUSENCIA` · `NIVEL_PROMOVIDO` ·
`DEVOLVIDA_PELO_EXECUTOR` *(acrescentado por ADR-0024 — não confundir com `ESCALADA`)*

**Bloco**
`BLOCO_AGENDADO` · `DOSSIE_MONTADO` · `DECIDIDA_NO_BLOCO`

**Comunicação**
`COMUNICADA` (canal de origem avisado) · `FALHA_COMUNICACAO` (havia canal, tentou e falhou) ·
`COMUNICACAO_IMPOSSIVEL` (não havia canal — item sem conversa inbound; ADR-0025) *(não confundir com `FALHA_COMUNICACAO`)*

**Assistente** (INV-10: registra proposta e desfecho, nunca execução)
`SUGESTAO_EMITIDA` · `SUGESTAO_ACEITA` · `SUGESTAO_RECUSADA` · `SUGESTAO_SILENCIADA`

**Correção**
`COMPENSACAO` — único mecanismo de correção. Referencia o evento compensado por id.

### Carga por evento
Todos: `pendencia_id`, `tipo`, `ator`, `ocorrido_em`, `origem`, `estado_anterior`, `estado_posterior`.
`EXECUTADA_POR_AUSENCIA` acrescenta: `delegacao_id`, `prazo`, `proposta`, `janela`, `intervencoes: []`.
`SUGESTAO_*` acrescenta: `tarefa`, `modelo`, `desfecho`.
