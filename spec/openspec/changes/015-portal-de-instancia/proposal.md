# 015 — Portal de instância + autoavaliação da contraparte

## Por quê
A esteira (012) processa a passagem de uma **entidade externa** (integrador, fornecedor). Hoje essa
contraparte **não tem como ver o estado nem agir** — cobra o comercial, que cobra o time (persona
P4). O RFC-0006 resolve com um **portal por link assinado, sem login**: a contraparte vê onde a
instância está e **declara conformidade com o checklist objetivo antes de submeter** — a economia mais
direta do módulo (elimina idas e vindas). Fecha o laço da esteira e do INV-09 (todo item tem contraparte).

## O quê
- **Token de instância** assinado (SHA-256; token cru devolvido uma vez), com expiração, escopo por
  instância, revogável — mesmo padrão do portal de pendência (007).
- **`GET /pi/{token}`** (público, sem login): esteira, etapa atual, entrada, **prazo previsto**
  (entrada + SLA da etapa) e **o que falta da contraparte** = os critérios **OBJETIVOS** do checklist
  da etapa do gestor. **Nunca** expõe deliberação interna, nomes de decisores nem outras contrapartes.
- **`POST /pi/{token}/autoavaliacao`** (público): a contraparte declara conformidade por critério
  objetivo. Fica registrado (auditável) e disponível ao gestor na avaliação.
- **Emissão/revogação** pelo gestor: `POST /v1/instancias/{id}/portal`, `POST /v1/instancias/portais/{tokenId}/revogar`.
- **Página pública** mínima (`/portal/instancia/{token}`), isenta do login, com o estado e o formulário.

## Privacidade (ADR-0013, regras de dados)
- O token **é** a credencial; o banco guarda só o hash. Resposta uniforme para inválido/expirado/
  revogado (sempre "não encontrado"). Cabeçalhos `no-index`. Sem dado de terceiros.
- A projeção pública é **curada**: só estado + prazo + o que falta; nada do interno.

## Fora de escopo
- Upload de documentos pela contraparte (só declaração de conformidade aqui).
- Notificação automática à contraparte (o link é entregue pelo gestor/canal; envio é 006/019).
- Reavaliação automática a partir da autoavaliação — ela **informa** o gestor, não decide (INV-10).

## Critério de aceite
- `GET /pi/{token}` devolve estado + prazo previsto + critérios objetivos ("o que falta"); token
  inválido/expirado/revogado → resposta uniforme de ausência.
- `POST /pi/{token}/autoavaliacao` grava as declarações escopadas à instância do token (INV-15).
- Nunca expõe deliberação/decisores/outras contrapartes; token guardado só como hash.
