# Design — 032 prazos úteis e lembretes por exceção

## Ordem

1. persistência e API ADMIN do calendário;
2. porta pura de cálculo de tempo útil;
3. agendamento 50/90 pelo tempo útil;
4. guardas de exceção nos handlers;
5. preferências e resumo agregado quando a identidade de canal estiver disponível.

## Segurança

Configuração é escopada por `org_id`; somente ADMIN altera. Datas são locais ao calendário, mas
jobs persistem UTC. Nenhuma métrica individual ou ranking é exposto.

## Riscos

DST, prazo em dia fechado e calendários vazios. Testes usam zonas com e sem mudança de offset e
rejeitam configuração incapaz de produzir tempo útil.

