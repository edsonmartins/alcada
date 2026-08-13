# Proposal — 032 prazos úteis e lembretes por exceção

## Problema

Lembretes atuais usam tempo corrido, podem sair fora do expediente e não distinguem atividade de
exceção acionável.

## Resultado

O motor acompanha contratos no calendário do tenant, notifica apenas quando há ação e deduplica
cada janela, sem exigir vigilância do gestor.

## Fora da primeira fatia

- sincronização automática de feriados externos;
- envio de resumo antes da Linktor resolver canal de pessoas internas;
- regras customizáveis por classe além dos percentuais normativos 50/90.

