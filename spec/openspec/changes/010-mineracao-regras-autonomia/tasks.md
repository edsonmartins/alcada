# Tasks — 010 mineração de regra de autonomia

## Migration
- [ ] `V13__regra_silenciada.sql`: tabela `regra_silenciada (id, org_id, classe, silenciada_em, por)`,
      único `(org_id, classe)`; GuardaOrgId conhece a tabela

## Backend — módulo regras (mineração determinística)
- [ ] `regras.port.Mineracao` + `PropostaRegra` (DTO: classe, ocorrencias, consistencia,
      nivelSugerido, donoSugerido, casos[])
- [ ] `regras.internal.MineracaoJdbc` — 90 dias; ocorrências (desfecho deliberado); reversões;
      consistência ≥95% e zero reversões e n≥min; exclui silenciadas e classes já com regra ativa;
      dono sugerido = mais frequente; casos[] (≤20) navegáveis
- [ ] Config `mineracao.min-ocorrencias` (padrão 15)
- [ ] `regras.port.Regras` + `RegraAtiva` (DTO); criar/desativar/silenciar

## Backend — API
- [ ] `GET /v1/regras/propostas` → candidatas com evidência
- [ ] `GET /v1/regras` → regras ativas
- [ ] `POST /v1/regras` {classe,nivel,donoId} — aceita (409 se já ativa; 422 se > nivel_maximo)
- [ ] `POST /v1/regras/propostas/silenciar` {classe}
- [ ] `POST /v1/regras/{id}/desativar`
- [ ] `@RegisterForReflection` dos DTOs; erros problem+json; docs/API.md

## Web — /alcadas
- [ ] Rota `/alcadas`: regras ativas + propostas
- [ ] Proposta com evidência clicável (casos → abrir trilha), aceitar (nível/dono) e silenciar
- [ ] Desativar regra ativa

## Testes
- [ ] Backend: cada cenário WHEN/THEN (proposta com evidência, poucos casos, reversão derruba,
      aceitar cria regra + motor roteia, 409 regra existente, 422 nivel_maximo, silenciar, desativar,
      isolamento org_id)
- [ ] Web (Vitest): lista propostas + evidência; aceitar/silenciar chamam a API

## Verificação
- [ ] JVM suite verde + build nativo + RSS ≤120 MB
- [ ] Deploy no piloto (GHCR pull) e conferência de /alcadas
