# Design — 035 resumo diário por exceção

## Ordem

1. migration do retrato único e evento de deliberação de retorno;
2. portas de leitura em triagem, autonomia e métricas;
3. compositor persistente substituindo as contagens do P032;
4. deliberação idempotente de retorno;
5. formatação Linktor e links para as ações web existentes;
6. testes de cenário, isolamento, corrida e regressão do resumo P032.

## Fronteiras

`notificacao` coordena, mas não lê tabelas de outros módulos. `triagem` fornece Hoje; `autonomia`
fornece N2, retornos e escalonamentos; `metricas` fornece a mediana. O retrato pertence a
`notificacao`. O endpoint de retorno chama uma porta publicada por `autonomia`.

## Concorrência e idempotência

Uma restrição única decide o vencedor da geração concorrente. O vencedor persiste o retrato e a
outbox na mesma transação. A deliberação de retorno usa lock pessimista e `Idempotency-Key`; nenhuma
atualização da Pendência ocorre implicitamente.

## Segurança e privacidade

Todas as leituras carregam `org_id`; o destinatário precisa ser o gestor associado ao alvo. O
envelope usa título e trecho já minimizado, nunca mensagem bruta, token ou endereço. A estimativa é
visível apenas ao próprio usuário, junto das ações, e não oferece comparação.

## Riscos

- definir “próximo período” quando só um horário está ativo: fallback para o próximo fechamento
  comercial precisa de teste;
- corrida entre geração e uma Saída: o retrato é consistente no instante da transação e o link pode
  encontrar item já resolvido sem repetir mensagem;
- pouco histórico: omitir a estimativa é preferível a inventar valor;
- compatibilidade de mensagens antigas durante deploy gradual do despachante.
