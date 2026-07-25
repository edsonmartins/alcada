# Tasks — 011 laço de aprendizado

## Migration
- [ ] `V14__pergunta_aprendizado.sql`: tabela + índice parcial único `(org_id, classe) WHERE ABERTA`;
      GuardaOrgId conhece `pergunta_aprendizado`

## Backend — módulo regras (laço)
- [ ] `regras.port.Aprendizado` + `PerguntaAprendizado` (DTO: id, classe, nivelSugerido, donoSugerido,
      ocorrencias, casos[]) + `Resposta` enum (SIM|AGORA_NAO|NAO_PERGUNTAR)
- [ ] `AprendizadoJdbc`: gerar (candidatas do Mineracao − abertas − recusadas-na-semana, teto 3/sem SP)
      + registrar SUGESTAO_EMITIDA; listar abertas com evidência
- [ ] responder: SIM→cria regra (Regras.criar)+ACEITA+SUGESTAO_ACEITA; AGORA_NAO→RECUSADA+
      SUGESTAO_RECUSADA; NAO_PERGUNTAR→SILENCIADA+SUGESTAO_SILENCIADA+Regras.silenciar

## Backend — API
- [ ] `GET /v1/aprendizado/perguntas` → gera + lista abertas (com evidência)
- [ ] `POST /v1/aprendizado/perguntas/{id}/responder` {resposta}
- [ ] `@RegisterForReflection`; erros problem+json; docs/API.md

## Web — /hoje
- [ ] Card de pergunta de aprendizado (uma por vez): classe, sugestão, evidência (casos → trilha),
      ações Sim / Agora não / Não perguntar

## Testes
- [ ] Backend: cada cenário WHEN/THEN (gera com evidência, 1 aberta/classe, teto 3/sem, sim cria
      regra, agora não sem silenciar, não perguntar silencia, sem evidência não pergunta, isolamento)
- [ ] Web (Vitest): card renderiza e as três respostas chamam a API

## Verificação
- [ ] JVM suite verde + build nativo + RSS ≤120 MB
- [ ] Deploy no piloto (GHCR pull) e conferência do card em /hoje
