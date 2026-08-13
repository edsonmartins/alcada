# Spec — 032 prazos úteis e lembretes por exceção

## C1 — admin configura calendário
**WHEN** ADMIN informa zona, dias e horário válidos
**THEN** configuração fica disponível apenas no tenant.

## C2 — não-admin não altera
**WHEN** usuário comum tenta configurar
**THEN** recebe 403 e nada muda.

## C3 — fim de semana não conta
**WHEN** intervalo atravessa sábado e domingo fechados
**THEN** frações e adições usam apenas dias úteis.

## C4 — feriado não conta
**WHEN** intervalo atravessa feriado configurado
**THEN** o dia inteiro é excluído.

## C5 — fuso e offset são preservados
**WHEN** calendário atravessa mudança de offset
**THEN** abertura local permanece estável e jobs persistem no instante UTC correto.

## C6 — lembrete de executor é exceção
**WHEN** chega 50% e delegação continua aberta sem proposta
**THEN** publica uma mensagem com ação para o executor.

## C7 — proposta suprime lembrete de executor
**WHEN** executor já propôs antes de 50%
**THEN** o job é no-op.

## C8 — gestor só recebe ação possível
**WHEN** chega 90% com silêncio e ainda há tempo útil para intervir
**THEN** publica uma mensagem acionável ao gestor.

## C9 — estado terminal suprime lembretes
**WHEN** delegação terminou, escalou ou foi devolvida
**THEN** jobs pendentes são no-op.

## C10 — lembretes são deduplicados
**WHEN** worker reprocessa o mesmo job
**THEN** existe uma mensagem no máximo.

## C11 — tenant não cruza calendário
**WHEN** duas organizações configuram calendários diferentes
**THEN** cada cálculo e agendamento usa apenas sua configuração.

## C12 — nenhuma mensagem de novo item
**WHEN** Pendência entra na Entrada
**THEN** P032 não publica notificação individual por esse evento.

