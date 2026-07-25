# Tasks — 010 mineração de regra de autonomia

## Migration
- [x] `V13__regra_silenciada.sql`: tabela `regra_silenciada (id, org_id, classe, silenciada_em, por)`,
      único `(org_id, classe)`; GuardaOrgId conhece a tabela

## Backend — módulo regras (mineração determinística)
- [x] `regras.port.Mineracao` + `PropostaRegra` (DTO: classe, ocorrencias, consistencia,
      nivelSugerido, donoSugerido, casos[])
- [x] `regras.internal.RegrasJdbc` (Mineracao+Regras) — 90 dias; ocorrências (desfecho deliberado);
      reversões; consistência ≥95% e zero reversões e n≥min; exclui silenciadas e classes já com
      regra ativa; dono sugerido = mais frequente; casos[] (≤20) navegáveis
- [x] Config `mineracao.min-ocorrencias` (padrão 15; 3 no profile de teste)
- [x] `regras.port.Regras` + `RegraAtiva` (DTO); criar/desativar/silenciar

## Backend — API
- [x] `GET /v1/regras/propostas` → candidatas com evidência
- [x] `GET /v1/regras` → regras ativas
- [x] `POST /v1/regras` {classe,nivel,donoId} — aceita (409 se já ativa; 422 se > nivel_maximo)
- [x] `POST /v1/regras/propostas/silenciar` {classe}
- [x] `POST /v1/regras/{id}/desativar`
- [x] `@RegisterForReflection` dos DTOs; erros problem+json; docs/API.md

## Web — /alcadas
- [x] Rota `/alcadas`: regras ativas + propostas
- [x] Proposta com evidência clicável (casos → abrir trilha), aceitar (nível/dono) e silenciar
- [x] Desativar regra ativa

## Testes
- [x] Backend: cenários WHEN/THEN — RegrasMineracaoTest (7): proposta+evidência, poucos casos,
      reversão derruba, aceitar cria regra + some das propostas, 409/422 via API, silenciar, isolamento
- [x] Web (Vitest): lista propostas + evidência; aceitar/silenciar chamam a API — alcadas.test.tsx

## Verificação
- [x] JVM suite verde (105) + 27 Vitest
- [x] build nativo (endpoints respondem, DTOs serializam) — RSS 70 MB
- [x] Deploy no piloto (GHCR pull) e conferência de /alcadas — laço minerar→aceitar→regra ativa
      verificado ao vivo (proposta some, regra passa a rotear)
