# Tasks — 004 trilha imutável

## Já entregue no bootstrap (Sessão 2)
- [x] Migration da `trilha` (append-only, particionada, `CHECK` de 29 tipos, `CHECK` de ator)
- [x] Trigger `BEFORE UPDATE OR DELETE` + `REVOKE` do role da aplicação
- [x] Porta `Trilha`, enum `TipoEvento` (29), `Ator`, escritor append-only (SQL nativo, sem UPDATE)
- [x] Testes: inserção; `UPDATE`/`DELETE` bloqueados pelo trigger (via superusuário)

## Vocabulário e carga
- [x] `EventoTrilha` cobrindo a carga específica por tipo (`EXECUTADA_POR_AUSENCIA`, `SUGESTAO_*`)
- [x] Evento `COMPENSACAO` com `evento_compensado_id` e motivo (`Trilha.compensar`)
- [x] Garantia de que `origem`/`carga` só carregam referências (validador rejeita CPF/CNPJ/e-mail/telefone)

## Rolagem de partição
- [x] Job SISTEMA de rolagem mensal, idempotente por mês, com folga de antecedência (`RolagemParticoes`)
- [x] Alerta se a partição `DEFAULT` receber linhas
- [x] Job de arquivamento frio implementado em V20 e coberto por `RolagemParticoesTest`

## Consulta
- [x] Porta de consulta por pendência + `GET /v1/pendencias/{id}/trilha`, filtrada por `org_id`

## LGPD
- [x] Pseudonimização do titular em `identidade` preservando referências da trilha (`Titulares`)
- [x] Teste de eliminação: cadeia íntegra, sem `UPDATE` na trilha

## Testes
- [x] Tipo fora da lista rejeitado; ator inválido rejeitado
- [x] Compensação não altera o original
- [x] Descarte por irrelevância não gera trilha (não há tipo de descarte no vocabulário)
- [x] Isolamento por organização na consulta
- [x] Rolagem idempotente + partição futura antes do uso
