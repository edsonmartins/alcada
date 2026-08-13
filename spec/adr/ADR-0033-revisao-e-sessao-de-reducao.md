# ADR-0033 — Revisão é sessão de redução, não relatório

**Status:** aceito · **Data:** 2026-08 · **Relacionado:** INV-01, INV-04, INV-06, INV-07, INV-08, ADR-0017, ADR-0018

## Contexto

A revisão semanal atual diagnostica Entrada, adiamentos e padrões, mas é somente leitura. O gestor
precisa sair do roteiro, agir em outras telas e voltar sem que a sessão saiba se alguma dependência
foi removida. Isso transforma o ritual em relatório e não demonstra encolhimento.

## Decisão

A revisão passa a ser uma **sessão de redução** com início e término explícitos. Ela guarda somente
identidade, intervalo e contadores agregados derivados da Trilha; não mantém cópia editável da fila.

Cada etapa oferece a ação de domínio já existente na superfície dona: triar, abrir Bloco de decisão,
resolver/repassar/repousar, deliberar proposta de regra e revisar promoção de nível. Depois da ação,
o roteiro relê o estado canônico. A sessão nunca executa em lote uma inferência nem inventa uma
quinta Saída.

O resultado principal é `dependenciasRemovidas`: Pendências que no início dependiam do gestor e, até
o fechamento, foram fechadas ou passaram a N1/N2. Contadores de transição permanecem separados. Uma
sessão sem transição é nomeada, conforme ADR-0018, sem score, ranking ou comparação pessoal.

Proteção do Horizonte TRIMESTRE é uma ação explícita e reversível sobre agenda; sugestão não cria
compromisso automaticamente. Se não houver integração de agenda configurada, o roteiro oferece a
superfície de configuração, sem simular sucesso.

## Consequências

- (+) A revisão mede redução real de dependência e termina com próxima ação.
- (+) Reusa comandos, validações, Trilha e janelas existentes.
- (-) Exige persistir a fronteira da sessão para apuração idempotente.
- (-) Promoção de autonomia só aparece com evidência suficiente e confirmação humana.
