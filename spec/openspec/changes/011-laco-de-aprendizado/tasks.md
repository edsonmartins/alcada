# Tasks — 011 laço de aprendizado

## Migration
- [x] `V14__pergunta_aprendizado.sql`: tabela + índice parcial único `(org_id, classe) WHERE ABERTA`;
      GuardaOrgId conhece `pergunta_aprendizado`

## Backend — módulo regras (laço)
- [x] `regras.port.Aprendizado` + `PerguntaAprendizado` (DTO) + `Resposta` enum (SIM|AGORA_NAO|NAO_PERGUNTAR)
- [x] `AprendizadoJdbc`: gera (candidatas do Mineracao − abertas − recusadas-na-semana, teto 3/sem SP)
      + registra SUGESTAO_EMITIDA; lista abertas com evidência
- [x] responder: SIM→cria regra (dono sugerido ou quem respondeu)+ACEITA+SUGESTAO_ACEITA;
      AGORA_NAO→RECUSADA+SUGESTAO_RECUSADA; NAO_PERGUNTAR→SILENCIADA+SUGESTAO_SILENCIADA+Regras.silenciar

## Backend — API
- [x] `GET /v1/aprendizado/perguntas` → gera + lista abertas (com evidência)
- [x] `POST /v1/aprendizado/perguntas/{id}/responder` {resposta} (404/409/422)
- [x] `@RegisterForReflection`; erros problem+json; docs/API.md

## Web — /hoje
- [x] Card de pergunta de aprendizado (uma por vez): classe, sugestão, evidência (casos → trilha),
      ações Sim / Agora não / Não perguntar

## Testes
- [x] Backend: cenários WHEN/THEN — AprendizadoTest (7): gera com evidência, 1 aberta/classe,
      sim cria regra, agora não sem silenciar, não perguntar silencia, sem evidência, isolamento
- [x] Web (Vitest): card renderiza + evidência; respostas chamam a API — aprendizado.test.tsx

## Verificação
- [x] JVM suite verde (112) + 29 Vitest
- [ ] build nativo + RSS ≤120 MB
- [ ] Deploy no piloto (GHCR pull) e conferência do card em /hoje
