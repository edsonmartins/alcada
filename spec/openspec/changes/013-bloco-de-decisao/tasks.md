# Tasks — 013 bloco de decisão

## Backend — módulo assistente
- [ ] `assistente.port.Bloco` + DTOs (BlocoDados{dossie[],opcoes[]}, RascunhoResultado)
- [ ] `assistente.internal.BlocoJdbc`: dossiê determinístico da pendência; opções por classe;
      `redigir` via ModelGateway (degrada p/ disponivel=false); `decidir` (FECHADA + DECIDIDA_NO_BLOCO
      + outbox decisao.comunicada; 409 se já fechada)

## Backend — API
- [ ] `GET /v1/pendencias/{id}/bloco`
- [ ] `POST /v1/pendencias/{id}/bloco/redigir` {opcao, tom}
- [ ] `POST /v1/pendencias/{id}/decidir` {opcao, texto}
- [ ] `@RegisterForReflection`; problem+json; docs/API.md

## Web — /bloco/{id}
- [ ] Dossiê (fatos + link para a trilha), opções com consequência
- [ ] Redação: escolher opção + tom, gerar rascunho editável (aviso se indisponível)
- [ ] Decidir e comunicar → fecha o item
- [ ] Entrada da tela: das pendências AGENDADA/ENTRADA (botão "Abrir bloco")

## Testes
- [ ] Backend: cenários WHEN/THEN (dossiê+opções; redigir rascunho; degradação sem modelo; decidir
      fecha+trilha+outbox; 409 já fechada; leitura pura ao montar; isolamento)
- [ ] Web (Vitest): bloco renderiza dossiê/opções; decidir chama a API

## Verificação
- [ ] JVM suite verde + build nativo + RSS ≤120 MB
- [ ] Deploy no piloto (GHCR pull) e conferência do bloco
