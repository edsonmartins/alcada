# RFC-0014 — Revisão semanal resolutiva

**Status:** implementado · **Implementa:** ADR-0033 · **Pacote:** 034

## Sessão

`POST /v1/revisao-semanal/sessoes` inicia ou devolve a sessão `ABERTA` do gestor na organização.
`GET /v1/revisao-semanal/sessoes/{id}` devolve o roteiro calculado no estado atual.
`POST /v1/revisao-semanal/sessoes/{id}/concluir` fecha idempotentemente e apura o resultado.

A sessão persiste `org_id`, `gestor_id`, início/fim e a população inicial de Pendências dependentes
do gestor. Ações continuam nos endpoints donos; cada releitura reconcilia a Trilha após a mutação.
Uma sessão aberta por gestor evita duplicidade e todas as consultas carregam `org_id`.

## Etapas fixas

1. **Entrada:** um item por vez com as quatro Saídas existentes; Repassar escolhe destino e Reservar
   usa o agendamento existente. Não há edição de metadados.
2. **Adiamentos recorrentes:** Resolver, Repassar, Repousar com data ou abrir Bloco.
3. **Regras:** propostas reais da mineração, evidências navegáveis e ações Aceitar, Recusar ou
   Observar. Recusar silencia a assinatura; Observar não ativa nem silencia e registra o desfecho.
4. **Autonomia:** Delegações concluídas fornecem candidatas conservadoras N3→N2 e N2→N1 quando há
   evidência; promoção altera regra de autonomia somente após confirmação humana.
5. **Trimestre:** mostra invasões do Horizonte TRIMESTRE e oferece proteção explícita de agenda.
6. **Fechamento:** transições separadas, dependências removidas e itens que continuam dependendo.

## Evidência e execução

Proposta e promoção exibem contagem e até cinco Pendências-fonte com links para `/itens/{id}`.
Inferência pode redigir condução, mas candidatos, elegibilidade e comandos são determinísticos.
Toda deliberação escreve Trilha append-only. Efeitos externos continuam pela outbox e respeitam a
janela de reversibilidade.

## Resultado

`ResumoSessao` contém `triadas`, `fechadas`, `repassadas`, `repousadas`, `blocosAbertos`,
`regrasAceitas`, `regrasRecusadas`, `regrasObservadas`, `niveisPromovidos`,
`protecoesAgenda`, `dependenciasRemovidas` e `continuamDependendo`. Não existe pontuação.

## Fora do pacote

- recomendação por modelo de quem deve receber uma Delegação;
- promoção automática ou ação em massa de regras;
- calendário estratégico completo ou gestão de capacidade;
- snapshot genérico de métricas para BI.
