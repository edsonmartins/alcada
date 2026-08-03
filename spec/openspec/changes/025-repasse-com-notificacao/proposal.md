# 025 — Repasse com notificação (destinatário interno ou externo)

**Fase:** F1 · **Implementa:** RFC-0008 · ADR-0013, ADR-0003 (parcial) · **Honra:** INV-02, INV-10, INV-11, INV-13, INV-14, INV-15
**Depende de:** 002 (motor), 019 (Linktor real), 021 (canal móvel), 022 (voz), 023 (trajeto)

## Problema
O repasse grava a delegação e a trilha, mas **não avisa ninguém**, e só resolve
destinatário **interno** (diretório de `pessoa`). O gestor então comunica por fora:
o processo racha em dois trabalhos e ele acaba achando mais fácil decidir e avisar
direto — furando a trilha, a janela e a autonomia, que é o valor do produto.

## Proposta
1. **Destinatário tipado** (`DestinoRepasse`): `Interno{pessoaId}` (tem fila no
   Alçada) ou `Externo{contatoId}` (contato, **não é conta** — INV-02).
2. **Contato externo** como dado operacional de repasse: `{nome, canal, endereco}`,
   canal `WHATSAPP|EMAIL`, escopado por org (INV-15) e PII (ADR-0011).
3. **Aviso pelo canal**: o repasse externo enfileira `AVISO_REPASSE` no outbox na
   mesma transação (INV-13); o despachante entrega por WhatsApp (Linktor) ou SMTP e
   registra `COMUNICADA` na trilha (INV-11). Nenhum módulo de domínio conhece o canal.
4. **Reversibilidade** (INV-14): o aviso só sai depois da janela; em trajeto nasce
   represado e é liberado na confirmação do resumo (ADR-0014).
5. **Comando e assistente**: `REPASSAR` aceita contato conhecido ou novo; o
   interpretador resolve o nome falado contra pessoas **e** contatos, e pergunta
   quando é ambíguo ou novo — nunca inventa destino (INV-10).

## Não-objetivos
- **Lembrete datado + calendário** (Google/Outlook) — RFC vizinho (F2).
- **Resposta do externo pelo portal** (aceitar/recusar/concluir) — já coberto por
  007/RFC-0006; aqui entra só o aviso.
- **Toque no canal para o destinatário interno** — questão aberta 4 do RFC-0008.
- **Dedup de contato por endereço** e reconciliação contato↔pessoa — questão aberta 2.

## Notas
Pacote escrito **depois** das fatias F1.2–F1.4a, que foram implementadas direto do
RFC-0008; registra o que existe e conduz o que falta (CLAUDE.md §6).
