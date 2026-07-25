# Tasks — 009 radar e revisão de sexta

## Backend — módulo metricas (leitura pura)
- [ ] `metricas.port.Radar` + `RadarDados` (DTO): dependeDoGestor, rodandoSemVoce, adiados[],
      piorEspera, autonomia, fechamentoCanal, encolhimento[8]
- [ ] `metricas.port.RevisaoSemanal` + `RevisaoDados` (DTO): entrada, adiados[], podeVirarRegra[],
      resumoSemana
- [ ] `metricas.internal.RadarJdbc` — consultas escopadas por org_id (predicado); janela 90d;
      contagem honesta (ADR-0024/0025); série 8 semanas por CAPTADA × fechamentos (SP)
- [ ] `metricas.internal.RevisaoJdbc` — entrada + adiados + dica de regra (≥3 RESOLVIDA/classe/4sem)
      + resumo da semana corrente (trilha)
- [ ] Helper de fuso: semanas ancoradas em America/Sao_Paulo; `toOdt` para timestamptz

## Backend — API
- [ ] `GET /v1/radar` (RadarResource) → RadarDados; problem+json em erro
- [ ] `GET /v1/revisao-semanal` (RevisaoResource) → RevisaoDados
- [ ] `@RegisterForReflection` dos DTOs (retornados via Response)
- [ ] docs/API.md: descrever as duas respostas (radar já constava; detalhar campos)

## Web — /radar
- [ ] Rota `/radar`: cards de métrica (dependeDoGestor, rodandoSemVoce, adiados, pior espera)
- [ ] Série de encolhimento (barras: entradas × fechamentos por semana) com leitura do INV-01
- [ ] Bloco de contagem honesta (autonomia + fechamento no canal)
- [ ] Cada card de padrão pessoal com **ação + causa** (ADR-0017); sem score/ranking

## Web — /sexta
- [ ] Rota `/sexta`: roteiro sequencial (entrada → adiados → dica de regra → resumo)
- [ ] Navegação passo a passo; ao fim, resumo da semana

## Testes
- [ ] Backend: cada cenário WHEN/THEN de spec.md (contagem de dependência, honestidade ADR-0024/0025,
      série 8 semanas, dica de regra, isolamento org_id, leitura pura)
- [ ] Web (Vitest): radar renderiza cards + série; sexta percorre os passos; "adiados" tem ação

## Verificação
- [ ] JVM suite verde + build nativo + RSS ≤120 MB
- [ ] Deploy no piloto (VPS) e conferência visual de /radar e /sexta
