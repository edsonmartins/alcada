# RFC-0012 — Prazos úteis e lembretes por exceção

**Status:** proposto · **Implementa:** ADR-0031 · **Pacote:** 032

## Modelo

`calendario_comercial` por organização guarda zona, dias úteis e horário. `feriado_comercial`
guarda datas e nomes. Configuração é administrativa e validada: zona IANA, ao menos um dia útil,
início anterior ao fim.

`preferencia_notificacao` por gestor guarda canal e horários permitidos para resumo. Ausência usa o
calendário da organização e não inventa endereço.

## Cálculo

Uma porta `CalendarioComercial` oferece `adicionarTempoUtil`, `fracaoDoIntervaloUtil` e
`proximaAbertura`. O cálculo itera intervalos locais e converte o resultado para UTC, incluindo
mudanças de offset da zona. Feriado fecha o dia inteiro.

Ao criar N2, 50% e 90% são calculados sobre tempo útil entre criação e prazo. Se o prazo não contém
tempo útil futuro, o comando é recusado. Vencimento e escalonamento mantêm instantes persistidos.

## Exceções

O job de 50% verifica estado `ABERTA` e ausência de proposta. O de 90% só avisa o gestor quando a
delegação ainda está `ABERTA` e existe tempo útil para intervir. Jobs obsoletos são no-op.

Resumo de início/fim do dia agrupa somente: vencimentos próximos sem proposta, pedidos de
informação vencidos e falhas de comunicação. Uma mensagem por gestor e período, com ações
clicáveis; nenhuma mensagem por item novo.

## Rollout

Primeira fatia: calendário/admin, cálculo útil e correção dos lembretes N2 com testes de fuso,
fim de semana e feriado. Resumo/canal interno fica atrás do gate Linktor até existir endereço
resolvido para gestores e executores.

