# ADR-0031 — Calendário comercial e notificação por exceção

**Status:** aceito · **Data:** 2026-08 · **Relacionado:** INV-04/07/14/15, ADR-0003/0013/0021

## Contexto

O motor já agenda lembretes em 50% e 90% do tempo corrido. Isso pode notificar à noite, em fim de
semana ou feriado e trata toda delegação ativa como exceção. O gestor volta a vigiar o contrato ou
passa a ignorar o canal.

## Decisão

Cada organização possui calendário comercial: fuso IANA, dias da semana, início/fim do expediente e
feriados locais. Durações operacionais são contadas apenas dentro desse calendário; instantes
informados explicitamente continuam absolutos, mas qualquer lembrete é deslocado para a próxima
abertura.

Lembretes são eventos de **exceção**, não avisos de atividade:

- 50%: somente ao executor e somente sem proposta;
- 90%: somente ao gestor se houver ação concreta possível antes do prazo;
- silêncio de ambos continua escalando deterministicamente;
- resumos de exceção agregam itens por gestor/período; nunca notificam “novo item”.

Toda mensagem declara a ação oferecida. Idempotência é por organização, destinatário, tipo e
janela temporal. Preferência de canal/horário restringe entrega; não altera o contrato.

## Consequências

- (+) reduz vigilância e ruído fora do expediente;
- (+) torna prazo sugerido reproduzível no fuso do tenant;
- (−) feriados exigem manutenção administrativa explícita;
- (−) validação real depende da resolução de canal de pessoas internas na Linktor.

