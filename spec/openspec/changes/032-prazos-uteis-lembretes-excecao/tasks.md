# Tasks — 032 prazos úteis e lembretes por exceção

## Especificação
- [x] ADR-0031 e RFC-0012
- [x] Proposal, design, spec e tasks

## Calendário
- [x] Migration V42 e GuardaOrgId
- [x] Porta de cálculo útil com fuso/feriado
- [x] API ADMIN de leitura e atualização
- [x] Testes C1–C5/C11

## Motor
- [x] Agendar 50/90 por tempo útil
- [x] Guardas sem proposta/ação possível/estado terminal
- [x] Idempotência e testes C6–C10/C12

## Resumo e preferências
- [x] Preferência de canal e horário por gestor (EMAIL enquanto único identificador interno confiável)
- [x] Resumo agregado início/fim do dia
- [x] Gate de identidade de canal Linktor documentado e fail-closed

## Validação
- [x] Suítes backend/web/typecheck (324 backend + 57 web em 2026-08-13)
- [ ] Native build
- [ ] Jornada ao vivo Linktor

## Evidência e limite da fatia local

- cálculo cobre fim de semana, feriado, isolamento de tenant e mudança de offset;
- 50%/90% usam tempo útil, proposta/estado terminal suprimem e outbox deduplica;
- nenhuma notificação de “novo item” foi introduzida;
- preferências e resumo agregado usam `pessoa.email` + fonte EMAIL Linktor; WhatsApp interno continua
  aberto por ausência de identidade confiável; não há envio fictício nem declaração ao vivo.
