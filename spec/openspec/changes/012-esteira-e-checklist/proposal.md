# 012 — Esteira, checklist versionado e mineração de checklist (§B)

## Por quê
Parte grande do backlog do gestor é o **mesmo processo repetido** — validar integrador, homologar
parceiro (ADR-0012): dez instâncias × seis portões = sessenta pendências que são, na verdade, seis
decisões. A esteira **elimina a classe, não o item** — é a maior alavanca de encolhimento (INV-01).

O checklist de esteira (RFC-0003 §B) depende deste substrato: só há o que minerar depois que
avaliações acumulam. Por isso este pacote entrega o **agregado Esteira** e, sobre ele, a **mineração
de critérios** (§B).

## O quê
- **Agregado Esteira** (ADR-0012, RFC-0006): `Esteira → Etapa[]`, `Instancia` (passagem de uma
  entidade externa), `Checklist` **versionado** por etapa do gestor, `Criterio {OBJETIVO|JULGAMENTO}`.
- **Regra de avanço**: todos os `OBJETIVO` obrigatórios aprovados **e** nenhum `JULGAMENTO` pendente
  → a instância **avança sem gerar pendência** para o gestor. Caso contrário → gera pendência
  (classe `ESTEIRA`) **com o resultado da avaliação anexado**, não a submissão crua.
- **Mineração de checklist (§B)**: sobre as reprovações da etapa, apontamentos **objetivos** que
  aparecem em **≥ 50%** viram critério candidato; apontamentos de **julgamento** são listados à
  parte (não viram checklist — permanecem com o gestor).
- **API**: `GET/POST /v1/esteiras`, `GET/POST /v1/esteiras/{id}/instancias`,
  `POST /v1/instancias/{id}/avaliar`, `POST /v1/instancias/{id}/avancar`,
  `GET/POST /v1/esteiras/{id}/checklist`, `GET /v1/esteiras/{id}/checklist/propostas`.
- **Web `/esteira`**: quadro de instâncias por etapa; avaliar; propostas de checklist (aceitar cria
  nova versão).

## Versionamento é obrigatório (ADR-0012, RFC-0003)
Nova versão de checklist **nunca** faz update — cria versão. Avaliações guardam `checklist_versao`; a
mineração lê cada avaliação contra a versão vigente à época.

## Fora de escopo
- **Portal de instância** e **autoavaliação da contraparte** (RFC-0006) — pacote próprio (o portal do
  007 é por pendência, não por instância).
- **Priorização por SLA com custo de atraso crescente** — integra depois; aqui a etapa tem `sla`
  registrado, sem entrar no score.
- **Assistente/dossiê** (F4).

## Critério de aceite
- Instância que passa em todos os objetivos obrigatórios (sem julgamento pendente) **avança sem
  pendência**; qualquer falha/julgamento gera pendência `ESTEIRA` com o resultado anexado.
- Checklist é versionado (nova versão nunca sobrescreve); avaliações referenciam a versão.
- A mineração propõe critério objetivo presente em ≥50% das reprovações e separa os de julgamento.
- Tudo escopado por `org_id` (INV-15); nenhuma promoção automática de critério (o gestor aceita).
