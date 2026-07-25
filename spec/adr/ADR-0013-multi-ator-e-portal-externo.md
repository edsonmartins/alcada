# ADR-0013 — Produto multi-ator; portal externo sem login

**Status:** aceito · **Data:** 2026-07 · **Relacionado:** INV-09

## Contexto
N2 exige que o executor receba a proposta e possa executar. O solicitante precisa ver estado para
parar de cobrar. A contraparte externa está esperando e não tem nenhum canal — cobra o comercial,
que cobra o time, que cobra o gestor.

## Decisão
Quatro superfícies de ator, com contratos distintos:

| Ator | Acesso | Vê |
|---|---|---|
| Gestor | conta completa | tudo do tenant |
| Executor | conta | itens delegados a ele, com nível e prazo |
| Solicitante | conta leve **ou** resposta no canal de origem | estado dos itens que pediu |
| Contraparte externa | **link assinado, sem login** | estado, prazo previsto, o que falta dela |

O portal externo expõe estado e pendência da contraparte — nunca deliberação interna, nomes de
decisores ou histórico de reprovação de terceiros.

Precificação: cobrança por **gestor ativo**. Executores e solicitantes não são cobrados; são o que
faz o mecanismo funcionar (ver PRODUTO §9).

## Consequências
- (+) Sem isso, N2 é mecanismo sem contraparte e o produto é app pessoal com relatório.
- (+) O portal corta o ruído de recobrança na origem e transforma atraso em número visível.
- (−) Multi-tenant com atores externos aumenta superfície de segurança; link assinado exige expiração,
  escopo e revogação.
