# 024 — Acompanhamento de grupos: extrair o que depende do gestor

**Fase:** F6 · **Implementa:** ADR-0011 (emendado por este pacote) · **Honra:** INV-10, INV-11, INV-14, INV-15 · ADR-0016, ADR-0017, ADR-0019, ADR-0020, ADR-0021
**Depende de:** 001 (captura multicanal), 019 (linktor-real), 018 (gateway de modelos)

## Problema
O cliente participa de **muitos grupos** (WhatsApp) onde surgem compromissos e
**demandas de decisão que dependem dele**. Ele não dá conta de ler tudo: coisas
passam batidas, o pessoal **cobra**, e a coisa não anda. É o problema central do
Alçada — "o que depende de você" — aplicado ao canal onde ele mais aparece.

Caso real (vira critério de aceite — ver `spec.md` C1): um grupo negocia uma
reunião ("cronograma e próximos passos"), a data/hora/participante/ação (mandar
invite) se espalham por ~10h e várias pessoas; hoje isso não vira nada no Alçada.

## Proposta
1. **Grupo como fonte declarada + bot visível** (ADR-0011 §1–2): admin cadastra o
   grupo como `fonte` com finalidade; o bot é identificável no grupo (aviso
   fixado), **nunca observador silencioso**.
2. **Contrato de grupo no Linktor** (F0): o webhook `message.received` passa a
   propagar `group{id,name}`, `sender{id,name}` (quem falou) e `mentions[]`. Hoje
   o Linktor tem essas primitivas (WhatsApp) mas as descarta no envelope. O
   Linktor segue **transporte burro** — nenhuma extração nele.
3. **Captura seletiva por padrão de decisão** (ADR-0011 §3, *varredura completa
   proibida*): um **pré-filtro barato** (determinístico) decide se o trecho sequer
   é candidato — menção ao gestor, resposta a item já rastreado, ou padrão de
   pedido/decisão/prazo. Ruído ("bom dia", "obrigada") nunca chega ao modelo. Log
   auditável da proporção processada.
4. **Extrator por janela** (F2): quando um candidato aparece e a conversa "esfria"
   (debounce), uma **janela de N mensagens** (com o remetente por linha,
   pseudonimizada pelo minimizador — ADR-0020 §3) vai ao modelo com a pergunta
   central: **"há aqui algo que depende de uma decisão/ação DELE?"**. O modelo
   **propõe** um compromisso estruturado (INV-10); o código executa o conjunto
   fechado.
5. **Compromisso vira pendência na Entrada** — no vocabulário fechado (ADR-0016).
   Persiste-se o **fato derivado** (assunto, quem pede, quando, ação, dono), não a
   transcrição (princípio RFC-014/ADR-0020). Re-hidratação local para **primeiro
   nome** (identidade mínima por finalidade — emenda ao ADR-0011 neste pacote).
6. **Sinal de cobrança → escala**: follow-up repetido sobre um item não resolvido
   = algo **parado esperando o gestor**; sobe prioridade e sinaliza ("já te
   cobraram 2x") — não cria item novo (funde no existente, ESCALADA).
7. **O filtro de descarte aprende "não é pra mim"** (011): o que o gestor descarta
   calibra o pré-filtro por grupo.

## Não-objetivos
- **Extração no Linktor.** Ele entrega mensagens com contexto de grupo; a
  inteligência (o que depende do gestor) é do Alçada.
- **Varredura completa / vigilância.** Nada de ler tudo; nada de placar sobre os
  membros do grupo (ADR-0017 — é a decisão *dele* que emerge, não métrica deles).
- **STT / interpretação de voz** (022) e **chat aberto** (ADR-0019) — fora daqui.
- **Agenda/calendário real** (criar o invite): este pacote **detecta e propõe** o
  compromisso; integrar calendário é pacote futuro.
