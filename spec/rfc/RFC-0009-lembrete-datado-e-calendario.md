# RFC-0009 — Lembrete datado e compromisso no calendário

**Status:** proposto · **Implementa:** ADR-0002, ADR-0009 (INV-10), ADR-0014 (voz/janela) · **Relaciona:** RFC-0002 (motor), RFC-0004 (assistente), RFC-0005 (mobile e voz), RFC-0008 (efeito externo pelo outbox), ADR-0008 (horizontes), ADR-0011/0020 (LGPD/minimização), ADR-0016 (trilha), ADR-0018 (anti-jardinagem), ADR-0021 (canais por adaptador)

## Objetivo

Deixar o gestor **fechar o item e guardar o compromisso na mesma frase**: um `RESOLVER` que também
cria um **lembrete datado** e, quando ele quiser, o **evento no calendário** dele.

Caso real (Rio Quality): *"resolvi a Sharpi, mas marquei a reunião pra quinta — me lembra"*. A
decisão foi tomada; o que sobra não é uma pendência aberta, é um **compromisso com data**.

## Situação atual (o gap)

- As quatro saídas (ADR-0002) **fecham** o item: `RESOLVER` manda para `FECHADA`. O que sobra ("na
  quinta tem reunião") não tem casa no Alçada.
- Sem casa, o gestor faz uma de três coisas, e as três custam caro:
  1. **Adia o item só para não esquecer.** O adiamento vira lembrete disfarçado e contamina o
     `adiado_count` — o sinal diagnóstico mais forte do sistema (ADR-0002, INV-01, Radar).
  2. **Anota fora** (calendário, papel). O processo racha em dois trabalhos — exatamente o que a
     RFC-0008 corrigiu no repasse.
  3. **Não anota.** A decisão que ele tomou não acontece.
- `REPOUSAR` chega perto (tem data e desperta), mas significa *"ainda não decidi"*. Aqui o gestor
  **decidiu**; o que fica é um compromisso.

## Modelo

Reusar a máquina que já existe, em vez de criar uma segunda caixa (INV-03, fila única):

```
resolver(pendencia, nota, lembrete?)
  Lembrete { quando: timestamptz, texto, comCalendario: bool }
```

- O `RESOLVER` fecha a pendência de origem (`FECHADA`, trilha `RESOLVIDA`) — o item sai da fila,
  como hoje.
- O lembrete nasce como **pendência nova em `DORMINDO`** (`volta_em = quando`), ligada à origem, e é
  agendado pelo `DESPERTAR` que já existe (`TriagemService`, idempotente por `ocorrencia`).
- Na data ele **aparece na Entrada** — mesma fila, mesma triagem, mesmas quatro saídas.
- **Não existe tela de lembretes** (ADR-0018): até despertar, ele é invisível.

Colunas novas em `pendencia`: `origem text` (`CAPTURA` | `ESCAPE` | `LEMBRETE`) e
`origem_pendencia_id uuid` (o item que foi resolvido), `evento_calendario_id text`.

**Por que pendência e não uma tabela `lembrete`:** fila única (INV-03); o despertar já é persistente,
idempotente e testado; e ao acordar o lembrete **é** um item que pede decisão ("a reunião é hoje —
preparar o quê?"). O custo é que um item `DORMINDO` de origem `LEMBRETE` não é captura: ele não pode
inflar o numerador de "entraram" no encolhimento (INV-01) — ver questão aberta 1.

**Classe e horizonte:** classe `DECISAO`; horizonte derivado de `quando` (ADR-0008 — `HOJE`,
`SEMANA`, `TRIMESTRE`). Título = o texto que o gestor falou ("Reunião Sharpi").

## Calendário (o compromisso)

```
Calendario (porta)
  ├─ GoogleCalendarHttp    OAuth do gestor, escopo mínimo (events)
  └─ OutlookHttp           mesma porta
```

- Criar evento é **efeito externo**: sai pelo **outbox** (`EVENTO_CALENDARIO`), nunca dentro do
  request, e só **depois da janela** (INV-14). Desfazer o `RESOLVER` na janela ⇒ o evento é
  descartado no outbox e **nunca existiu**. Em trajeto, fica represado como os demais (ADR-0014).
- **OAuth por gestor, não por tenant** — o calendário é pessoal. Token cifrado, escopo mínimo,
  revogável na tela de sessão. Indisponível no SKU on-premise sem internet (ADR-0028).
- O `evento_calendario_id` volta para a pendência-lembrete: permite cancelar quando o gestor desfaz
  e detectar cancelamento do lado do calendário.
- **Nenhum módulo de domínio conhece Google/Microsoft** — mesma regra do Linktor (ADR-0021); o
  adaptador vive em `notificacao`, que já é o dono da saída.

## Fluxo

```
"resolvi a Sharpi, mas marquei reunião quinta 10h"
  └─ assistente PROPÕE: RESOLVER {pendencia} + LEMBRETE {2026-08-06T10:00-03:00, "Reunião Sharpi"}
       └─ gestor confirma (uma frase, um "sim" — ADR-0014 §2)
            ├─ pendência → FECHADA + trilha RESOLVIDA               [determinístico, INV-10]
            ├─ pendência-lembrete DORMINDO + DESPERTAR na data      [scheduler persistente]
            └─ outbox EVENTO_CALENDARIO (se comCalendario)          [só após a janela, INV-14]
                  └─ Calendario.criarEvento → evento_id → trilha COMPROMISSO_AGENDADO

na data: DESPERTADA → o lembrete aparece na Entrada
```

## Extração da data (INV-10)

- O modelo devolve `quando` em **ISO-8601 já resolvido** contra o fuso do tenant ("quinta" →
  `2026-08-06T10:00-03:00`) e o `texto`; o **código valida** (futuro, dentro de 12 meses, horário
  comercial quando a hora não foi dita) e agenda.
- Data ausente ou ambígua ⇒ o assistente **pergunta** ("quinta que vem, dia 6?"). Nunca chuta — o
  mesmo padrão do destino no RFC-0008. Um lembrete na data errada é pior que nenhum: o gestor confia
  e perde a reunião.

## Trilha (INV-11)

Acrescentar ao anexo do ADR-0016:

- `LEMBRETE_CRIADO` — na pendência de **origem**, ator `HUMANO:{gestor}`, payload com o id do
  lembrete e o `quando` (liga origem → lembrete).
- `COMPROMISSO_AGENDADO` / `FALHA_COMPROMISSO` — na pendência-lembrete, ator `SISTEMA:motor:despacho`.

`DESPERTADA` já cobre o acordar; `DESFEITA_NA_JANELA` cobre o desfazer.

## Reversibilidade (INV-14)

- **Dentro da janela:** desfazer o `RESOLVER` reabre a origem, fecha o lembrete e descarta o
  `EVENTO_CALENDARIO` no outbox — o calendário do gestor nunca soube.
- **Depois da janela:** cancelar o lembrete enfileira `CANCELAR_EVENTO_CALENDARIO` (o evento existe;
  some do calendário pelo mesmo caminho).

## Offline (INV-13)

O lembrete nasce do comando móvel (`campos.lembrete`), na mesma fila offline e idempotente por
`comandoId`: sem rede, o `RESOLVER` e o lembrete ficam pendentes juntos e sincronizam depois. O
evento de calendário só sai quando houver rede — nada se perde.

## Contrato (portas)

```java
// triagem
void resolver(OrgId org, UUID pendenciaId, String nota, Lembrete lembrete, UUID gestorId);
record Lembrete(OffsetDateTime quando, String texto, boolean comCalendario) {}

// notificacao — nenhum domínio conhece Google/Microsoft
interface Calendario {
    String criarEvento(OrgId org, UUID gestorId, OffsetDateTime quando, Duration duracao, String titulo);
    void cancelarEvento(OrgId org, UUID gestorId, String eventoId);
}

interface ContasCalendario {
    Optional<Conta> doGestor(OrgId org, UUID gestorId);   // provedor + token (cifrado)
    void conectar(OrgId org, UUID gestorId, String codigoOauth);
    void revogar(OrgId org, UUID gestorId);
}
```

## Invariantes tocadas

INV-01 (o lembrete não pode inflar a fila nem mascarar o encolhimento) · INV-02 (nasce da fala, não
de cadastro) · INV-03 (fila única — o lembrete volta pela Entrada) · INV-10 (o modelo propõe a data;
o código agenda) · INV-11 (trilha) · INV-12/ADR-0020 (token OAuth é segredo; o título do evento não
trafega pelo gateway de modelos) · INV-13 (offline, outbox) · INV-14 (janela antes do calendário) ·
INV-15 (`org_id` em tudo).

## Fatias

- **F2.1** modelo e motor: `RESOLVER` com lembrete, pendência-lembrete, despertar, trilha — **sem** calendário.
- **F2.2** comando móvel + interpretador: data por voz, validação e a pergunta quando ambíguo.
- **F2.3** porta `Calendario` + adaptador Google (OAuth, criar/cancelar pelo outbox).
- **F2.4** Outlook no mesmo contrato.
- **F2.5** web: conectar/revogar o calendário na sessão; o lembrete visível na Entrada ao despertar.

## Questões em aberto

1. **Métricas.** O lembrete conta como item que "entrou" (encolhimento, INV-01)? Proposta: `origem =
   LEMBRETE` fica **fora** do numerador de entradas e do `adiado_count`; conta só quando o gestor
   age sobre ele.
2. **Sync bidirecional.** Mover ou cancelar a reunião no Google **não** move o lembrete nesta RFC.
   Fazer isso exige webhook/polling e reconciliação — RFC própria.
3. **Lembrete sem origem** ("me lembra de ligar pro Paulo quinta") — é o `REGISTRAR` com data. Mesma
   máquina, ou intenção separada?
4. **Recorrência** ("toda segunda"): provavelmente **não** (ADR-0018 — o Alçada não é agenda).
5. **Duração padrão** do evento (1h?) e se o Alçada **convida participantes**. Convidar é efeito
   sobre terceiro e exige o cuidado da RFC-0008 (consentimento, canal) — proposta: não convida.
6. **Fuso do gestor viajando**: ancorar ao fuso do tenant (regra atual) ou ao do dispositivo?

## Fora de escopo

- **Calendário como fonte de captura** (ler compromissos e virar pendência): é captura, não saída —
  RFC vizinha, junto do e-mail como canal de entrada.
- **Bloco de decisão** (RFC-0004, pacote 013) continua sendo o mecanismo de **reservar tempo para
  decidir**. O lembrete não agenda trabalho: ele lembra de um compromisso já assumido.
