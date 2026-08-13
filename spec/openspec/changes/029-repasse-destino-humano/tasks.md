# Tasks — 029 repasse para destino reconhecível

## Decisões antes do código
- [x] Contrato tipado aprovado; `/delegar` antigo mantido durante o piloto
- [x] Normalização/deduplicação escopada por org, com PII fora do gateway
- [x] Prazo sugerido somente quando há histórico comparável; horário comercial continua no P032

## Backend
- [x] Consulta unificada compõe portas publicadas de pessoas e contatos
- [x] `GET /v1/destinos-repasse` com limite, ordenação, máscara e `org_id`
- [x] Corpo tipado em `/repassar`; `/delegar` mantém o corpo antigo
- [x] Transação contato novo + delegação + outbox + trilha
- [x] Deduplicação serializada por canal/endereço normalizado dentro do tenant
- [x] Sugestões determinísticas de nível e prazo, sem modelo
- [x] `docs/API.md` atualizado

## Web
- [x] Campo UUID substituído por combobox pesquisável
- [~] Recentes/equipe/contatos identificados no rótulo; agrupamento visual fica para polimento
- [~] Contato novo inline com validação obrigatória; formalizar schema Zod específico
- [x] Nível, prazo e confirmação explícita permanecem no contrato
- [~] Componentes operáveis por teclado; teste completo de restauração de foco pendente

## Testes
- [~] Backend cobre união, máscara, dedup e isolamento; ampliar rastreabilidade C1–C14/C17–C18
- [~] Web cobre ausência de UUID; ampliar estados e fluxo completo C15–C18
- [x] Regressões mobile/voz da suíte existente permanecem verdes

## Validação
- [ ] Teste integrado com pessoa interna e contato WhatsApp/e-mail
- [ ] Observar tempo e erros de repasse no piloto sem criar métrica individual
- [ ] Atualizar `ESTADO-PRODUTO.md` após validação ao vivo
