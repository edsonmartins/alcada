# Design — 034 revisão resolutiva

## Fatias

1. sessão persistente e apuração por Trilha;
2. Entrada e adiados com ações existentes incorporadas;
3. propostas de regra com três desfechos auditáveis;
4. candidatas conservadoras de promoção de nível;
5. impacto trimestral e proteção explícita de agenda;
6. fechamento com dependências removidas e remanescentes.

## Persistência

`sessao_revisao` guarda cabeçalho e resumo; `sessao_revisao_dependencia` guarda apenas os ids do
conjunto inicial. Restrição parcial garante uma sessão `ABERTA` por `(org_id, gestor_id)`. Nenhuma
cópia de título, conteúdo ou estado vira fonte concorrente.

## Fronteiras

O módulo `metricas` coordena leitura e apuração, mas não escreve diretamente em tabelas de triagem,
autonomia ou regras. A web chama os endpoints donos e invalida a sessão. Novos comandos necessários
serão publicados por portas dos módulos correspondentes.

## Promoção conservadora

Uma candidata exige pelo menos três desfechos deliberados compatíveis no nível atual, sem devolução
ou escalonamento na janela de 90 dias. N3 sugere N2; N2 sugere N1. A confirmação cria/ajusta regra
de autonomia dentro do `nivel_maximo` da Classe. Ausência de dono elegível impede a ação.

## Trimestre

Impacto é a contagem e o valor de itens operacionais ocupando Horizonte TRIMESTRE, acompanhados de
fontes. A proteção agenda um bloco reservado pelo mecanismo de calendário existente, com comando
explícito, idempotência e desfazer; não há otimização automática da agenda.

## Riscos

- corrida entre uma ação e o fechamento: resumo é apurado transacionalmente pela Trilha;
- dupla sessão: índice parcial e retorno idempotente da sessão aberta;
- contagem inflada: Pendências distintas, nunca quantidade bruta de eventos;
- pressão para virar dashboard: roteiro fixo e término obrigatório preservam ADR-0018.

