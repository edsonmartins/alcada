# RFC-0008 — Repasse com notificação (destinatário interno ou externo)

**Status:** proposto · **Implementa:** ADR-0013, ADR-0003 (parcial) · **Relaciona:** RFC-0002 (motor), RFC-0006 (portal externo), RFC-0007 (destino por sensibilidade), ADR-0005 (escape), ADR-0011 (LGPD), ADR-0014 (janela/trajeto)

## Objetivo

Fazer o **repasse comunicar sozinho** o executor — por WhatsApp ou e-mail — e permitir que o
destinatário **não seja usuário do Alçada**. Hoje o repasse resolve apenas um `pessoa` interno e não
avisa ninguém: o gestor comunica por fora, o processo **racha em dois trabalhos**, e ele acaba achando
mais fácil decidir/avisar direto — furando a trilha, a janela e a autonomia (o valor do Alçada).

## Situação atual (o gap)

- `repassar` (motor, RFC-0002) grava `delegacao` + evento `REPASSADA`, mas **não notifica** o executor.
- A resolução do destino é **só interna** (diretório de `pessoa`); um nome desconhecido vira "não achei".
- A infra de saída **já existe** e é reusável: `outbox` transacional → `Despachante` →
  `DespachanteCanal` → `LinktorHttp` (WhatsApp), e o **portal externo** (`token_portal`,
  `EmissaoPortalResource`, ADR-0013/RFC-0006) para quem age sem ter conta.

## Modelo

O repasse passa a ter um **destinatário tipado**:

```
Destinatario =
  | INTERNO  { pessoa_id }                         -- membro do tenant (tem fila no Alçada)
  | EXTERNO  { contato_id }                        -- contato, NÃO é usuário

ContatoExterno { id, org_id, nome, canal: WHATSAPP|EMAIL, endereco, criado_por, criado_em }
```

- `ContatoExterno` é **dado operacional de repasse**, não uma conta — segue a lógica do escape
  (ADR-0005): o gestor o cria em falha/exceção, é métrica, não o caminho feliz (INV-02 preservado:
  ninguém "cadastra usuário" à mão).
- `nivel` (N1/N2, ADR-0003) continua valendo para ambos os tipos.
- Multi-tenant: `ContatoExterno` e `delegacao` carregam `org_id` (INV-15).

## Fluxo

```
repassar(pendencia, destinatario, nivel)
  ├─ grava delegacao + trilha REPASSADA (ator HUMANO:{gestor})     [determinístico, INV-10]
  └─ enfileira no OUTBOX  AVISO_REPASSE {delegacao_id}             [transacional, mesma tx]
                                   │
        WorkerOutbox ─► GatewayDeDestino ─► canal                  [RFC-0007 estendido: destino]
                                   │            ├─ EXTERNO WHATSAPP → LinktorHttp
                                   │            ├─ EXTERNO EMAIL    → e-mail
                                   │            └─ INTERNO          → fila (pull) + toque opcional no canal
                                   ├─ EXTERNO: emite token de portal (RFC-0006) → link na mensagem
                                   └─ entrega ⇒ trilha COMUNICADA (ator SISTEMA:motor:despacho)
```

- **Gateway de destino** espelha o de modelos (RFC-0007): o chamador declara *quem* e *o quê*; o
  gateway decide *o canal*. Nenhum módulo de domínio conhece Linktor/SMTP.
- O executor **externo** responde pelo **portal** (aceitar / recusar / concluir) — sem login; a
  resposta vira evento na trilha e reflete no estado da pendência.
- O executor **interno** vê na própria fila (pull, CLAUDE.md §8); o toque no canal é um reforço opcional.

## Reversibilidade (INV-14) e trajeto

- A comunicação ao terceiro é **efeito externo**: só dispara **após a janela** de reversibilidade.
  Desfazer dentro da janela ⇒ o `AVISO_REPASSE` é descartado no outbox e **o terceiro nunca soube**.
- Em **trajeto** (RFC-0005/ADR-0014), o aviso fica **represado** até estacionar e é liberado na
  confirmação do resumo — igual aos demais efeitos represados.

## Idempotência e entrega

- `outbox` com `idempotency_key = (delegacao_id, 'AVISO_REPASSE')` — reprocesso não duplica mensagem.
- Retry exponencial; nada se perde (INV-13). Falha definitiva ⇒ trilha `FALHA_COMUNICACAO` e o item
  volta à atenção do gestor (não fica silenciosamente sem dono).

## LGPD (ADR-0011)

- `ContatoExterno.endereco` (telefone/e-mail) é **PII**: minimização, retenção e base legal como no
  resto da captura. **Não trafega pelo gateway de modelos** — é dado operacional, não entra em prompt.
- Consentimento/again-legal do contato externo é questão aberta (abaixo).

## Trilha (INV-11)

Vocabulário existente cobre o essencial: `REPASSADA`, `COMUNICADA`, `FALHA_COMUNICACAO`. A **resposta
do externo** (aceite/recusa/conclusão pelo portal) precisa de eventos — reusar os do portal (RFC-0006)
ou acrescentar ao anexo do ADR-0016 é decisão de spec.

## Assistente (INV-10)

O assistente **propõe** o repasse: resolve o nome falado contra (a) o diretório interno e (b) os
contatos externos conhecidos; se ambíguo/novo, **pergunta** (nunca inventa destino). Um nome novo pode
virar `ContatoExterno` na hora, com confirmação — o mesmo padrão do "aprender apelido" (022).

## Contrato (porta)

```java
sealed interface DestinoRepasse permits Interno, Externo {}
record Interno(UUID pessoaId) implements DestinoRepasse {}
record Externo(UUID contatoId) implements DestinoRepasse {}

interface Repasse {
    void repassar(OrgId org, UUID pendenciaId, DestinoRepasse destino, Nivel nivel, UUID gestorId);
    UUID registrarContato(OrgId org, String nome, Canal canal, String endereco, UUID gestorId);
}
```

## Invariantes tocadas

INV-02 (contato ≠ conta; criação é escape/métrica) · INV-10 (propõe/executa) · INV-11 (trilha) ·
INV-13 (outbox, nada se perde) · INV-14 (janela antes de comunicar) · INV-15 (org_id em tudo).

## Questões em aberto

1. **Consentimento/LGPD** do contato externo receber mensagem — base legal e opt-out.
2. **Dedup de contato** (mesmo telefone/e-mail sob nomes diferentes) e reconciliação com `pessoa`
   quando o externo depois vira usuário.
3. **Eventos de resposta do externo** — reusar os do portal (RFC-0006) ou ampliar o ADR-0016.
4. **Interno também no canal?** — decidir se o membro interno recebe toque no WhatsApp além da fila.
5. **Custo/limite** de envio por canal (anti-spam, throttling) no gateway de destino.

## Fora de escopo (RFC vizinho)

**Lembrete datado + integração de calendário (Google/Outlook).** Caso real: o gestor *resolve* uma
decisão mas agenda um compromisso para outra data e precisa ser lembrado (ex.: "resolvi a Sharpi, mas
marquei a reunião pra quinta"). Isso é um **RESOLVER que também cria um lembrete datado**, e idealmente
**cria o evento no calendário** do gestor (OAuth Google/Outlook) — o lembrete dispara pelo motor de
autonomia (DESPERTAR/LEMBRETE, RFC-0002) na data. Merece **RFC próprio** (OAuth, criação de evento,
sync bidirecional, disparo do lembrete), separado deste. Este RFC cobre só a **notificação do repasse**.
