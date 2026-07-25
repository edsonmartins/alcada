# Deploy — bootstrap do piloto

## Seed do tenant (`seed.sql`)

Idempotente e parametrizado por **variáveis de ambiente** — nada fixo no arquivo. O `webhook_secret`
é **credencial**: entra por env, nunca é commitado nem impresso.

### Variáveis

| Variável | Obrigatória | Default | O que é |
|---|---|---|---|
| `ALCADA_ORG_ID` | ✅ | — | uuid da organização (tenant) |
| `ALCADA_ORG_NOME` | | `Organização Piloto` | nome |
| `ALCADA_ORG_SKU` | | `CLOUD` | `CLOUD` ou `SOBERANO` |
| `ALCADA_GESTOR_ID` | ✅ | — | uuid do gestor |
| `ALCADA_GESTOR_NOME` | | `Gestor` | nome |
| `ALCADA_EXECUTOR_ID` | ✅ | — | uuid do executor |
| `ALCADA_EXECUTOR_NOME` | | `Executor` | nome |
| `ALCADA_CLASSE` | | `DECISAO` | classe de decisão |
| `ALCADA_JANELA` | | `PT2M` | **janela de reversibilidade curta** (ISO-8601) — o cronômetro dispara em 2 min na demo |
| `ALCADA_ESCALONAMENTO` | | `PT24H` | escalonamento por silêncio de ambos |
| `ALCADA_FONTE_ID` | ✅ | — | uuid da fonte (canal) |
| `ALCADA_FONTE_TIPO` | | `WHATSAPP` | tipo |
| `ALCADA_FONTE_IDENTIFICADOR` | | `grupo-piloto` | rótulo do grupo |
| `ALCADA_LINKTOR_CHANNEL_ID` | ✅ | — | `channelId` do canal no Linktor (resolve webhook→fonte) |
| `ALCADA_FONTE_SEGREDO` | ✅ | — | **webhook_secret** do canal Linktor (HMAC do inbound) — **credencial** |

### Rodar

```bash
# gere uuids uma vez e guarde (para re-rodar idempotente):
export ALCADA_ORG_ID=$(uuidgen) ALCADA_GESTOR_ID=$(uuidgen) \
       ALCADA_EXECUTOR_ID=$(uuidgen) ALCADA_FONTE_ID=$(uuidgen)
# do Linktor (passo manual): channelId + webhook_secret do canal
export ALCADA_LINKTOR_CHANNEL_ID=<channelId>
export ALCADA_FONTE_SEGREDO=<webhook_secret>   # credencial — não commitar

psql "$ALCADA_DB_URL" -f deploy/seed.sql
```

Re-rodar é seguro: `ON CONFLICT` atualiza config (janela, segredo, channelId) sem duplicar.

## Plano B da demo — inserir pendência sem Linktor

Se o WhatsApp cair ao vivo, injete um item direto na fila pelo **escape manual** (ADR-0005),
sem depender do Linktor:

```bash
curl -X POST https://<alcada>/v1/pendencias \
  -H "X-Org-Id: $ALCADA_ORG_ID" -H "X-Pessoa-Id: $ALCADA_GESTOR_ID" \
  -H 'Content-Type: application/json' \
  -d '{"titulo":"Aprovar reembolso do Rafael","quemEspera":"Rafael","classe":"DECISAO"}'
```

O item entra em `ENTRADA`, pronto para delegar N2 → cronômetro → executar por ausência → trilha.
(Sem canal de origem, o fechamento vira `COMUNICACAO_IMPOSSIVEL` — coerente com o ADR-0025.)
