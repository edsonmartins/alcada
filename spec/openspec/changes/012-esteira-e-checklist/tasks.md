# Tasks — 012 esteira, checklist e mineração §B

## Migration
- [x] `V15__esteira.sql`: esteira, etapa, checklist, criterio, instancia, avaliacao, apontamento;
      GuardaOrgId conhece as novas tabelas

## Backend — módulo esteira (agregado)
- [x] `esteira.port`: DTOs + interfaces `Esteiras`, `Avaliacoes`, `MineracaoChecklist`
- [x] `EsteiraJdbc`: criar/listar esteira+etapas; criar/listar instância por etapa
- [x] `avaliar`: grava avaliação + apontamentos; regra de avanço (aprovada → avança sem pendência;
      falha/julgamento → pendência ESTEIRA com resultado anexado + trilha CAPTADA)
- [x] `avancar`: move a instância para a próxima etapa (ou CONCLUIDA)
- [x] Checklist versionado: `GET` versão vigente; `POST` cria nova versão (nunca update)
- [x] `propostas` (§B): reprovações 90d; apontamento OBJETIVO em ≥50% → candidato; JULGAMENTO à parte;
      mínimo configurável (`esteira.checklist.min-reprovacoes`, padrão 4)

## Backend — API
- [x] `GET/POST /v1/esteiras`; `GET/POST /v1/esteiras/{id}/instancias`
- [x] `POST /v1/instancias/{id}/avaliar`; `POST /v1/instancias/{id}/avancar`
- [x] `GET/POST /v1/esteiras/{id}/checklist`; `GET /v1/esteiras/{id}/checklist/propostas`
- [x] `@RegisterForReflection`; erros problem+json; docs/API.md

## Web — /esteira
- [x] Rota `/esteira`: quadro de instâncias; nova instância
- [x] Avaliar (resultados por critério + apontamento); ver desfecho
- [x] Propostas de checklist (objetivo/julgamento); aceitar → nova versão

## Testes
- [x] Backend: cenários WHEN/THEN — EsteiraTest (7): aprovada avança sem pendência; falha gera
      pendência anexada; julgamento pendente; versionamento; mineração ≥50%; poucas reprovações;
      isolamento
- [x] Web (Vitest): quadro + instância + propostas de checklist — esteira.test.tsx

## Verificação
- [x] JVM suite verde (119) + 30 Vitest
- [x] build nativo (endpoints respondem) — RSS 71 MB
- [x] Deploy no piloto (GHCR pull); /esteira verificado (aprovada avança, reprovadas geram pendência, §B propõe critério)
