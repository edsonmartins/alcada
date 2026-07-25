# Tasks — 012 esteira, checklist e mineração §B

## Migration
- [ ] `V15__esteira.sql`: esteira, etapa, checklist, criterio, instancia, avaliacao, apontamento;
      GuardaOrgId conhece as novas tabelas

## Backend — módulo esteira (agregado)
- [ ] `esteira.port`: DTOs (Esteira, Etapa, Instancia, Checklist, Criterio, Avaliacao, Proposta) +
      interfaces `Esteiras`, `Avaliacoes`, `MineracaoChecklist`
- [ ] `EsteiraJdbc`: criar/listar esteira+etapas; criar/listar instância por etapa
- [ ] `AvaliacaoJdbc.avaliar`: grava avaliação + apontamentos; regra de avanço (aprovada → avança sem
      pendência; falha/julgamento → pendência ESTEIRA com resultado anexado + trilha CAPTADA)
- [ ] `avancar`: move a instância para a próxima etapa (ou CONCLUIDA)
- [ ] Checklist versionado: `GET` versão vigente; `POST` cria nova versão (nunca update)
- [ ] `MineracaoChecklistJdbc`: reprovações 90d; apontamento OBJETIVO em ≥50% → candidato;
      JULGAMENTO à parte; mínimo configurável

## Backend — API
- [ ] `GET/POST /v1/esteiras`; `GET/POST /v1/esteiras/{id}/instancias`
- [ ] `POST /v1/instancias/{id}/avaliar`; `POST /v1/instancias/{id}/avancar`
- [ ] `GET/POST /v1/esteiras/{id}/checklist`; `GET /v1/esteiras/{id}/checklist/propostas`
- [ ] `@RegisterForReflection`; erros problem+json; docs/API.md

## Web — /esteira
- [ ] Rota `/esteira`: quadro de instâncias por etapa
- [ ] Avaliar (resultados + apontamentos); ver desfecho
- [ ] Propostas de checklist (objetivo/julgamento); aceitar → nova versão

## Testes
- [ ] Backend: cenários WHEN/THEN (aprovada avança sem pendência; falha gera pendência anexada;
      julgamento pendente; versionamento; mineração ≥50%; julgamento à parte; poucas reprovações;
      aceite cria versão; isolamento)
- [ ] Web (Vitest): quadro + avaliar + propostas de checklist

## Verificação
- [ ] JVM suite verde + build nativo + RSS ≤120 MB
- [ ] Deploy no piloto (GHCR pull) e conferência de /esteira
