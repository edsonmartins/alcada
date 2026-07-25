# Tasks — 013 bloco de decisão

## Backend — módulo assistente
- [x] `assistente.port.Bloco` + DTOs (BlocoDados{dossie[],opcoes[]}, RascunhoResultado)
- [x] `assistente.internal.BlocoJdbc`: dossiê determinístico; opções por classe; `redigir` via
      ModelGateway (degrada p/ disponivel=false); `decidir` (FECHADA + DECIDIDA_NO_BLOCO + outbox
      decisao.comunicada; 409 se já fechada)

## Backend — API
- [x] `GET /v1/pendencias/{id}/bloco`
- [x] `POST /v1/pendencias/{id}/bloco/redigir` {opcao, tom}
- [x] `POST /v1/pendencias/{id}/decidir` {opcao, texto}
- [x] `@RegisterForReflection`; problem+json; docs/API.md

## Web — /bloco/{id}
- [x] Dossiê (fatos + link para a trilha), opções com consequência
- [x] Redação: escolher opção + tom, gerar rascunho editável (aviso se indisponível)
- [x] Decidir e comunicar → fecha o item
- [x] Entrada da tela: link "abrir bloco" nos cards da Entrada

## Testes
- [x] Backend: cenários WHEN/THEN — BlocoTest (5): dossiê+opções; redigir rascunho; decidir
      fecha+trilha+outbox; 409 já fechada; isolamento
- [x] Web (Vitest): bloco renderiza dossiê/opções; decidir chama a API — bloco.test.tsx

## Verificação
- [x] JVM suite verde (124) + 31 Vitest
- [x] build nativo — RSS 67 MB
- [x] Deploy no piloto (GHCR pull); bloco verificado ao vivo (dossiê+opções+redação degradada+decidir)
