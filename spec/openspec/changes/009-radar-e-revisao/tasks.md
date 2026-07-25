# Tasks — 009 radar e revisão de sexta

## Backend — módulo metricas (leitura pura)
- [x] `metricas.port.Radar` + `RadarDados` (DTO): dependeDoGestor, rodandoSemVoce, adiados[],
      piorEspera, autonomia, fechamentoCanal, encolhimento[8]
- [x] `metricas.port.RevisaoSemanal` + `RevisaoDados` (DTO): entrada, adiados[], podeVirarRegra[],
      resumoSemana
- [x] `metricas.internal.RadarJdbc` — consultas escopadas por org_id (predicado); janela 90d;
      contagem honesta (ADR-0024/0025); série 8 semanas por CAPTADA × fechamentos (SP)
- [x] `metricas.internal.RevisaoJdbc` — entrada + adiados + dica de regra (≥3 RESOLVIDA/classe/4sem)
      + resumo da semana corrente (trilha)
- [x] Helper de fuso: semanas ancoradas em America/Sao_Paulo; params de tempo por OffsetDateTime

## Backend — API
- [x] `GET /v1/radar` (RadarResource) → RadarDados; problem+json em erro
- [x] `GET /v1/revisao-semanal` (RevisaoResource) → RevisaoDados
- [x] `@RegisterForReflection` dos DTOs (retornados via Response)
- [x] docs/API.md: descrever as duas respostas

## Web — /radar
- [x] Rota `/radar`: cards de métrica (dependeDoGestor, rodandoSemVoce, adiados, pior espera)
- [x] Série de encolhimento (barras: entradas × fechamentos por semana) com leitura do INV-01
- [x] Bloco de contagem honesta (autonomia + fechamento no canal)
- [x] Cada card de padrão pessoal com **ação + causa** (ADR-0017); sem score/ranking

## Web — /sexta
- [x] Rota `/sexta`: roteiro sequencial (entrada → adiados → dica de regra → resumo)
- [x] Navegação passo a passo; ao fim, resumo da semana

## Testes
- [x] Backend: cenários WHEN/THEN de spec.md (dependência, honestidade ADR-0024/0025,
      série 8 semanas, dica de regra, isolamento org_id) — RadarRevisaoTest (7)
- [x] Web (Vitest): radar renderiza cards + contagem honesta; sexta percorre os passos — radar.test.tsx

## Verificação
- [x] JVM suite verde (98) + 25 Vitest
- [x] build nativo (endpoints respondem, DTOs serializam) — RSS 72 MB
- [x] Deploy no piloto via GHCR (pull) e conferência visual de /radar e /sexta
