# Tarefas — 020 consulta em linguagem natural

- [x] Módulo `consulta` (port/internal) + fronteira ArchUnit atualizada
- [x] Whitelist de templates parametrizados (enum) sobre `pendencia`, org-escopado
- [x] `ConsultaJdbc`: LLM (gateway.extrair, schema estrito, atrás de `consulta.usar-llm`) monta a consulta; fallback determinístico por palavras-chave (demo)
- [x] Resposta montada do resultado, com itens como fonte navegável
- [x] Pergunta fora da whitelist → "não sei responder isso sobre a fila" (C4)
- [x] `POST /v1/consulta` + reflexão para native
- [x] Testes: C1 esperando-mim, C2 travado-por, C3 aversivos, C4 desconhecido, C5 isolamento org
- [x] UI: caixa de consulta (Radar) com resposta + itens clicáveis
- [x] Verificar: JVM verde (142 testes) + native build + RSS = 75MB (≤120MB), endpoint OK
- [ ] Deploy e conferência
