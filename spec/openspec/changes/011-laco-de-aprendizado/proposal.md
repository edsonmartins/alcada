# 011 — Laço de aprendizado

## Por quê
O 010 minera regras e as expõe em `/alcadas` — mas depende de o gestor ir olhar. O **laço de
aprendizado** (RFC-0003, ADR-0019) é o front **proativo**: quando um padrão vira candidato, o
sistema faz **uma** pergunta situada, "onde o gestor estiver", em vez de esperar que ele navegue até
a tela. É assim que critério tácito vira regra explícita — o ativo do produto (INV-01).

## O quê
- Pergunta de aprendizado com **três respostas** (ADR-0019): `sim` (cria a regra), `agora não`,
  `não perguntar isso` (silencia a classe permanentemente).
- **Com evidência clicável** — os N casos navegáveis (ADR-0019: proibido sugerir autonomia sem
  evidência). Reaproveita a mineração do 010.
- **Disciplina** (RFC-0003): no máximo **1 pergunta aberta por classe**; no máximo **3 por semana**;
  `agora não` não re-pergunta na mesma semana; `não perguntar` silencia de vez.
- **Trilha**: `SUGESTAO_EMITIDA` ao perguntar; `SUGESTAO_ACEITA` / `SUGESTAO_RECUSADA` /
  `SUGESTAO_SILENCIADA` na resposta (vocabulário já existente). A recusa é sinal negativo auditável.
- **API**: `GET /v1/aprendizado/perguntas` (gera sob demanda + lista abertas com evidência),
  `POST /v1/aprendizado/perguntas/{id}/responder {resposta}`.
- **Web**: card de pergunta em `/hoje` (superfície diária), com as três ações e a evidência.

## Integração com o 010
- Candidata = proposta do `Mineracao` (010). `sim` cria `regra_autonomia` com o nível/dono sugeridos
  (o "sim" É a confirmação humana — INV-10). `não perguntar` grava `regra_silenciada` (010), parando
  mineração **e** perguntas.

## Fora de escopo
- **Checklist de esteira** (RFC-0003 §B) e o critério de julgamento — pacote próprio.
- Pergunta ancorada num critério específico ("foi por causa dos campos fiscais?") — depende do §B;
  aqui a pergunta é sobre transformar o padrão da **classe** em regra.
- Assistente de dossiê/redação (F4).

## Critério de aceite
- No máximo 1 pergunta aberta por classe e no máximo 3 criadas por semana (por organização).
- `sim` cria a regra (nível/dono sugeridos) e registra `SUGESTAO_ACEITA`; a classe passa a ser roteada.
- `agora não` registra `SUGESTAO_RECUSADA` e não re-pergunta a classe na mesma semana.
- `não perguntar` registra `SUGESTAO_SILENCIADA`, silencia a classe (não volta em perguntas nem em
  propostas do 010).
- Evidência clicável presente em toda pergunta; nada é criado sem resposta humana; escopo por `org_id`.
