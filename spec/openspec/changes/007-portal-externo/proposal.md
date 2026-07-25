# 007 — Portal externo sem login

## Por quê
A contraparte externa (P4 — integrador, fornecedor) está esperando e **não tem canal**: não está no
grupo, não tem conta. Ela cobra o comercial, que cobra o time, que cobra o gestor — cada salto
adiciona ruído sem informação (ADR-0013, INV-09). Um link assinado que mostra estado e o que falta
dela corta esse ruído na origem e transforma atraso em número visível.

## O quê
- **Token de portal**: link assinado, sem login, com **expiração, escopo por item e revogação**
  (ADR-0013). Emissão e revogação são ações internas do tenant.
- **`GET /p/{token}`**: projeção **pública** do estado de uma pendência — estado (grosso), data de
  entrada, prazo previsto, **o que falta da contraparte**.
- **Fronteira dura**: nunca expõe deliberação interna, nome de decisor, status interno detalhado nem
  dado de outra contraparte. Cabeçalhos `no-index`.
- **Isolamento**: um token só enxerga o item ao qual foi escopado; nunca cruza pendência ou tenant.

## Fora de escopo
- **Autoavaliação / checklist** (`POST /p/{token}/autoavaliacao`) — depende da **esteira** (pacote
  014, RFC-0006); aqui o portal é somente leitura.
- **Esteira e instância** como conteúdo do portal — quando a esteira existir (014), o mesmo mecanismo
  de token passa a apontar para instâncias; nesta fase aponta para pendências.
- **Notificação ativa à contraparte** (enviar o link por e-mail/canal) — o link é gerado; a entrega é
  do módulo de canais quando houver endereço da contraparte.

## Critério de aceite
- Token válido devolve estado público; expirado ou revogado é recusado.
- Nenhum campo interno (decisor, deliberação, status detalhado, outras contrapartes) aparece na
  resposta pública.
- A resposta carrega cabeçalho `no-index`.
- Token escopado a uma pendência não revela outra, nem item de outro tenant.
