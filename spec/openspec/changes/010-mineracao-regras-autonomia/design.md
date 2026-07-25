# Design — 010 mineração de regra de autonomia

## Determinístico, humano confirma (INV-10)
Nenhum modelo. A mineração é SQL sobre trilha+pendência. A regra só passa a existir quando um humano
aceita. O modelo nunca promove alçada.

## Assinatura e desfecho
- **Assinatura** (chave da regra, limite do motor): `classe`.
- **Ocorrências**: pendências **fechadas** nos últimos 90 dias da classe que tiveram desfecho
  deliberado — trilha com `RESOLVIDA` **ou** `EXECUTADA` **ou** `EXECUTADA_POR_AUSENCIA` **ou**
  `DECIDIDA_NO_BLOCO`.
- **Reversão** (sinal negativo, RFC): trilha da pendência com `INTERROMPIDA`, `DESFEITA_NA_JANELA`,
  `DEVOLVIDA_PELO_EXECUTOR` ou `ESCALADA`.
- **Consistência**: fração das ocorrências **sem reversão**. Candidata quando `consistencia >= 0.95`
  **e** `reversões == 0` **e** `n >= min` (config `mineracao.min-ocorrencias`, padrão 15).

## Nível sugerido
Objetivo é migrar para autonomia. Como a classe fechou de forma consistente e sem reversão, a
sugestão padrão é **N1** (a regra passa a rotear automaticamente). O **dono sugerido** é o `dono_id`
mais frequente entre as delegações da classe; se não houver, fica nulo e o humano escolhe no aceite.
A sugestão nunca é aplicada sozinha (INV-10).

## Evidência clicável (ADR-0019)
A proposta carrega `casos[]` = até 20 pendências da classe: `{pendenciaId, titulo, desfecho,
valorEmJogo}`. O front torna cada caso navegável (abre a trilha). Sem casos, não há proposta.

## Guardas ao aceitar
- Recusa se já existe `regra_autonomia` ativa para a classe (`409`).
- Recusa `nivel` acima de `classe_decisao.nivel_maximo` da organização (`422`).
- `dono_id` obrigatório no aceite (a delegação automática precisa de dono).

## Silenciar
`POST /v1/regras/propostas/silenciar {classe}` grava em `regra_silenciada (org_id, classe)`. A
mineração exclui classes silenciadas. Reversível por nova mineração? Não — silenciar é decisão do
gestor; reativar é ação explícita futura (fora de escopo).

## Migration
`V13__regra_silenciada.sql`: tabela `regra_silenciada (id, org_id, classe, silenciada_em, por)` com
único `(org_id, classe)`. `regra_autonomia` e `classe_decisao` já existem.

## API
```
GET  /v1/regras                         -> regras ativas {id, classe, nivel, donoId, criadaEm}
GET  /v1/regras/propostas               -> candidatas {classe, ocorrencias, consistencia, nivelSugerido, donoSugerido, casos[]}
POST /v1/regras            {classe, nivel, donoId}   -> cria regra (humano confirma)
POST /v1/regras/propostas/silenciar {classe}
POST /v1/regras/{id}/desativar
```

## Multi-tenant e reflexão
Todo predicado com `org_id` (INV-15; GuardaOrgId). DTOs via `Response` → `@RegisterForReflection`.
Mineração é leitura; criar/silenciar/desativar são escritas simples (sem outbox — não há efeito
externo; a aplicação da regra acontece na próxima captura, já existente).

## Fora do design
- Assinatura por faixa de valor / tipo de solicitante / escopo (exige motor de aplicação por faixa).
- Checklist de esteira e laço de aprendizado (pacotes próprios).
