# Spec — 033 consulta completa de itens

## C1 — pergunta 1: “onde está a decisão do contrato Alfa?”
**WHEN** busca por título ou conteúdo minimizado
**THEN** encontra aberta ou fechada, mostra estado e link para detalhe em até duas interações.

## C2 — pergunta 2: “o que está com Carolina?”
**WHEN** filtra pessoa ou busca pelo nome do executor
**THEN** devolve somente itens cuja última Delegação pertence a Carolina.

## C3 — pergunta 3: “o que foi decidido na semana passada?”
**WHEN** filtra período e estado fechado ou usa consulta natural
**THEN** devolve decisões com fonte na trilha e link navegável.

## C4 — pergunta 4: “quais N2 continuam abertos?”
**WHEN** combina nível N2 e estado aberto
**THEN** lista contratos correspondentes sem misturar N1/N3 ou fechados.

## C5 — pergunta 5: “o que veio do grupo Fiscal?”
**WHEN** filtra/busca origem reconhecível
**THEN** devolve itens daquela origem sem expor identificador bruto desnecessário.

## C6 — fechadas e abertas coexistem
**WHEN** não há filtro de estado
**THEN** ambas aparecem com estado explícito e ordenação fixa por atividade.

## C7 — detalhe traz história
**WHEN** abre um resultado
**THEN** vê resumo, última Delegação e trilha paginada, sem poder editar metadados.

## C8 — fonte conduz à ação situada
**WHEN** resultado possui bloco, Delegação, regra ou instância
**THEN** link profundo aponta para a superfície existente correspondente.

## C9 — busca não lê bruto
**WHEN** termo existe apenas no bruto retido pela Linktor
**THEN** não é encontrado; somente campos estruturados e conteúdo minimizado são pesquisáveis.

## C10 — paginação é limitada
**WHEN** tamanho excede 100 ou página é inválida
**THEN** rejeita ou limita deterministicamente sem consulta ilimitada.

## C11 — isolamento por organização
**WHEN** termo, id ou pessoa existe em outro tenant
**THEN** retorna vazio/404 sem revelar existência.

## C12 — consulta não vira fila
**WHEN** usuário abre `/itens`
**THEN** não encontra drag-and-drop, edição em massa, prioridade, etiqueta ou visão salva.

