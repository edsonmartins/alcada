# ADR-0012 — Esteira como agregado próprio, com checklist versionado

**Status:** aceito · **Data:** 2026-07 · **Relacionado:** ADR-0006

## Contexto
Parte relevante do backlog do gestor é o mesmo processo repetido: validar integrador, aprovar
fornecedor, homologar parceiro. Dez instâncias por ano × seis portões = sessenta pendências que são,
na verdade, seis decisões.

## Decisão
Modelar **Esteira** como agregado distinto de pendência avulsa:

- `Esteira` tem `Etapa[]` ordenadas, cada uma com dono e SLA.
- `Instancia` é a passagem de uma entidade externa pela esteira.
- Etapas do gestor têm `Checklist` **versionado**, com critérios objetivos e critérios de julgamento
  marcados separadamente.
- Instância que passa em todos os critérios objetivos avança sem gerar pendência para o gestor.
  Falha ou critério de julgamento gera pendência com o resultado já anexado.
- O checklist inicial é **proposto por mineração** das últimas N decisões da mesma etapa (RFC-0003) e
  confirmado pelo gestor.

## Consequências
- (+) Maior alavanca de encolhimento do produto: elimina classe, não item.
- (+) Habilita autoavaliação pela contraparte antes da submissão.
- (−) Depende de o critério tácito ser objetivo. Se for julgamento puro, a esteira degrada para
  pendência avulsa — o produto sobrevive, com menos valor.
- (−) Versionamento é obrigatório: decisões antigas precisam ser lidas contra o critério vigente à época.

## Gate
Confirmar com o cliente-piloto: (a) o critério é objetivo? (b) o gargalo é validar ou é entender o
que a contraparte enviou? Se for (b), a alavanca é o formato de submissão, e a esteira muda de desenho.
