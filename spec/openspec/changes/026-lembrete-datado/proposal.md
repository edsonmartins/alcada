# 026 — Lembrete datado e compromisso no calendário

**Fase:** F2 · **Implementa:** RFC-0009 · ADR-0002, ADR-0008, ADR-0014 · **Honra:** INV-01, INV-02, INV-03, INV-10, INV-11, INV-13, INV-14, INV-15
**Depende de:** 002 (motor/despertar), 021 (canal móvel), 022 (voz), 025 (efeito externo pelo outbox)

## Problema
O gestor decide e sobra um **compromisso com data**: *"resolvi a Sharpi, mas marquei a
reunião pra quinta — me lembra"*. As quatro saídas fecham o item; o compromisso não
tem casa. Sem casa, ele **adia o item só para não esquecer** — e o `adiado_count`,
o sinal diagnóstico mais forte do sistema (ADR-0002, INV-01), vira ruído; ou anota
fora, e o processo racha em dois, como no repasse antes da RFC-0008.

## Proposta
1. `RESOLVER` aceita um **lembrete datado** `{quando, texto}`: o item fecha e o
   compromisso vira uma **pendência nova em `DORMINDO`**, ligada à origem.
2. No dia, o `DESPERTAR` que já existe traz o lembrete para a **Entrada** — fila
   única (INV-03), quatro saídas de sempre, **sem tela de lembretes** (ADR-0018).
3. O **evento no calendário** do gestor (Google/Outlook) é efeito externo: sai pelo
   outbox e só **depois da janela** (INV-14). OAuth por gestor, não por tenant.
4. A data vem do modelo já resolvida em ISO-8601; o **código valida** (futuro, ≤12
   meses) e agenda. Ambígua ⇒ o assistente pergunta (INV-10).

## Não-objetivos
- **Sync bidirecional** com o calendário (mover/cancelar do lado do Google).
- **Recorrência** ("toda segunda") — o Alçada não é agenda (ADR-0018).
- **Convidar participantes**: efeito sobre terceiro, exigiria o cuidado da RFC-0008.
- **Calendário como fonte de captura** — é entrada, RFC vizinha.
