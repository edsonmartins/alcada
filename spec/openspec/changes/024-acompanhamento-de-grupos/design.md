# Design — 024 acompanhamento de grupos

## Fluxo ponta a ponta
```
Grupo (WhatsApp)
  → Linktor (adaptador WhatsApp: já tem is_group, chat_jid, sender_jid, mentions)
  → webhook message.received  [F0: passa a propagar group{}, sender{}, mentions[]]
  → POST /v1/captura/linktor   [HMAC por fonte; resolve org+segredo pelo channelId]
  → Captura: MensagemRecebida (+ isGrupo, grupoId, autorExt=sender)  [F1]
  → Pré-filtro determinístico (candidato? senão descarta e loga proporção)  [F1]
  → Extrator por janela (debounce → janela N msgs → minimizador → gateway LLM)  [F2]
  → Proposta de compromisso (JSON, INV-10)  → re-hidrata (1º nome) → funde/cria Pendência  [F2]
  → Entrada do gestor; cobrança → ESCALADA  [F3]
```

## F0 — contrato de grupo no Linktor (mudança cirúrgica, nós controlamos)
As primitivas já existem no adaptador WhatsApp (`IncomingMessage.IsGroup`,
`ChatJID`, `SenderJID`, `Mentions`, `GroupInfo{Name,Topic,Participants}`), mas o
dispatcher do webhook (`inboundMetadata`/`InboundData`) só propaga
`senderName`/`externalId`. Adicionar ao `data` do `message.received`:
```jsonc
"group":   { "id": "<chat_jid>", "name": "<nome>" },   // ausente/null = 1:1
"sender":  { "id": "<sender_jid|phone>", "name": "<nome, se houver>" },
"mentions": ["<jid>", ...]                               // @-menções na mensagem
```
- `group.id` estável → chave da conversa de grupo no Alçada.
- `sender` → **quem falou** (para atribuir dono/quem pede).
- `mentions` → sinal forte de "o gestor foi marcado" (depende dele).
- **Participantes** ficam **sob demanda** (`GET group/{id}` ou evento `group.info`),
  não em cada mensagem — minimização (ADR-0011 §5). Pega-se só quando preciso
  resolver um nome.
- Garantir `ignore_groups = false` no canal do cliente.

## F1 — captura ciente de grupo
- `MensagemRecebida` ganha `boolean grupo` e `String grupoId` (o `autorExt` e
  `threadRef` já existem; para grupo `threadRef = grupoId`, `autorExt = sender.id`).
- O grupo é uma `fonte` declarada (ADR-0011 §1): admin cadastra com finalidade;
  `fonte` carrega `grupo=true` e `linktor_channel_id`/`grupoId`.
- **Bot visível** (ADR-0011 §2): ao ativar a fonte-grupo, publicar aviso fixado no
  grupo via canal de saída (Linktor). Nunca captura sem o aviso ativo.
- **Pré-filtro determinístico** (ADR-0011 §3 — captura seletiva; e escala/custo):
  um trecho só é candidato se (a) menciona o bot/gestor (`mentions`), OU (b)
  responde/segue um item já rastreado no grupo, OU (c) casa com **padrão de
  decisão** (léxico de pedido/prazo/agendamento/aprovação + interrogação
  direcionada). Ruído puro é descartado **antes** do modelo. Log auditável da
  proporção processada por fonte.

## F2 — extrator por janela (padrão do `SentimentWindowScheduler` do vendax)
- **Gatilho:** apareceu candidato E a conversa do grupo "esfriou" por
  `grupos.debounce-seconds` (default 90s), avaliada em poll persistente
  (`grupos.poll-ms`, INV: sem timer em memória). Marca de progresso por sequência
  na conversa (`avaliado_ate_seq`) para não reprocessar; avança **antes** da
  resposta do modelo, dentro da transação/outbox.
- **Janela:** últimas `grupos.window-size` (default 20) mensagens do grupo, em
  ordem, **com o remetente por linha**.
- **Minimizador (ADR-0020 §3, obrigatório):** antes do gateway, pseudonimizar
  nomes/telefones/e-mails → tokens estáveis por janela; truncar ao trecho
  relevante; **re-hidratação local** da saída. O modelo nunca vê PII direta.
- **Gateway (RFC-0007):** chamador declara `Sensibilidade`; política ZDR fixa
  (`only`, `allow_fallbacks:false`, `data_collection:deny`, `zdr:true`,
  `require_parameters:true`). Schema estrito (json_schema) — sem json_object.
- **INV-10:** o modelo devolve uma **proposta**; nenhum efeito externo no caminho.

### Schema de saída (proposta de compromisso)
```jsonc
{
  "dependeDoGestor": true,                 // false → descarta (não vira item)
  "tipo": "REUNIAO|APROVACAO|DECISAO|FOLLOW_UP|OUTRO",
  "assunto": "cronograma atualizado e próximos passos",
  "quemPede": { "token": "P1" },           // re-hidratado p/ 1º nome local
  "quando": { "textoOriginal": "próxima segunda 14h", "resolvido": "2026-08-03T14:00" },
  "acaoPendente": "enviar invite de calendário",
  "possivelmenteFeito": true,              // ex.: "enviei!" no fio
  "confianca": 0.0,
  "sourceMessageIds": ["<id>", "..."],     // idempotência/rastreio (auditoria)
  "grupoId": "<chat_jid>"
}
```
- `dependeDoGestor=false` ou `confianca` baixa → **não** vira pendência (só loga).
- O código valida/normaliza (data absoluta pelo fuso do tenant; tipo no conjunto
  fechado) e **funde ou cria** a `Pendencia` (INV-10 executa o conjunto fechado).

## F3 — superfície + cobrança + aprendizado
- O compromisso entra na **Entrada** como pendência (CAPTADA, ator
  `ASSISTENTE:{modelo,versão}` — INV-11), com `classe` derivada do `tipo` e o
  `valorEmJogo`/prazo quando houver.
- **Cobrança:** novo candidato que casa com item já aberto (mesmo assunto/thread)
  **funde** (FUNDIDA) e incrementa um contador de cobrança; ao passar um limiar,
  **ESCALADA** + rótulo "já te cobraram Nx". Não cria item novo.
- **Descarte realimenta o pré-filtro** (011): descartar um item ensina o padrão a
  suprimir similar naquele grupo.

## Persistência e retenção
- Persiste-se o **fato derivado** (a pendência + campos do schema), **não** a
  transcrição. Bruto (janela) retido ≤30 dias só para correção de extração
  (ADR-0011 §4), depois expira.
- Idempotência por `(grupoId, message.id)` na captura e por `sourceMessageIds` na
  extração (reprocesso não duplica).
- INV-15: toda query com `org_id`; nada de aprendizado cruza tenant.

## Decisões em aberto (para revisão)
1. **Vocabulário (ADR-0016):** cobrança precisa de evento próprio
   (`COBRANCA_DETECTADA`) ou basta ESCALADA + contador? *Proposta: reusar ESCALADA
   + campo contador; não inflar o vocabulário.*
2. **Padrão de decisão do pré-filtro:** léxico fixo vs configurável por fonte.
   *Proposta: léxico base + termos por fonte, calibrados pelo descarte.*
3. **Debounce vs tempo-real para cobrança urgente** (gestor marcado explicitamente):
   *Proposta: menção direta ao gestor fura o debounce (avalia na hora).*
