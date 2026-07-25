# ADR-0022 — Nome do produto: Alçada

**Status:** aceito · **Data:** 2026-07 · **Encerra:** gate G1

## Contexto
O produto operou sob o codinome `Fila`, genérico e não protegível. O nome aparece em pontos de alta
exposição: o bot dentro do grupo do cliente, o portal que a contraparte externa abre sem login, e a
conversa em que o gestor explica ao time por que as decisões passam a ter prazo.

Foram avaliadas três famílias: metáfora da fila de decisão, metáfora da autoridade concedida e
neutros internacionalizáveis. `Docket` foi descartado após verificação: já é marca ativa em B2B SaaS
com IA, com captação relevante e imprensa — colisão de marca, não apenas de domínio.

## Decisão
**Alçada.** Domínio primário `alcada.app`.

Razões:
- nomeia o **mecanismo**, não o objeto. A tese do produto é que o problema não é organização, é
  escopo de autoridade mal definido — e o nome já diz isso
- vocabulário que o mercado brasileiro corporativo já usa; não exige ensinar o conceito
- impossível de confundir com gerenciador de tarefas
- concorrente global dificilmente ocupa o termo

## Convenções obrigatórias

O nome colide com um termo do próprio domínio. Para evitar ambiguidade:

| Uso | Forma | Exemplo |
|---|---|---|
| Produto | **Alçada**, sempre em maiúscula, feminino com artigo | "a Alçada não fala com mensageria" |
| Conceito de autoridade | alçada, minúscula | "a alçada do gerente comercial" |
| Regra do sistema | **regra de autonomia** — nunca "regra de alçada" | "promover para regra de autonomia" |

A superfície web usa **Regras** como rótulo de navegação; a rota `/alcadas` foi mantida por
compatibilidade e os endpoints passaram a `/v1/regras`.

## Alternativas rejeitadas
- **Docket** — colisão de marca em B2B SaaS com IA
- **Crivo** — bom de marca, risco de conflito no INPI a verificar
- **Leeway / Clearance** — fortes, mas o cliente-alvo e o discurso de soberania são brasileiros
- **Fila** — genérico e não registrável

## Consequências
- (+) Nome carrega a tese; reduz explicação em venda e onboarding
- (+) Coerente com o posicionamento de soberania e mercado nacional
- (−) Primeiro produto do portfólio em substantivo comum português; desalinha da estética de
  Mentors, VendaX, ArchGate e Linktor. Escolha consciente
- (−) Exige disciplina de convenção para não colidir com o termo de domínio
- (−) Trava exportação futura; nome internacional exigiria segunda marca

## Pendências
1. Registro no INPI, classes 9 e 42 — **não iniciado**
2. Domínios defensivos: `alcada.com.br` e `alcada.io` estavam disponíveis a custo baixo; avaliar
   registro junto com o `.app`
3. `alcada.app` exige HTTPS obrigatório (TLD com HSTS preload) — sem impacto, já é o padrão
