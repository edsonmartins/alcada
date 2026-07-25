# Tasks — 007 portal externo sem login

## Token
- [x] Migration `V11`: tabela `token_portal` (hash, escopo por pendência, expira_em, revogado) + guarda org_id
- [x] Geração de token opaco de alta entropia (SecureRandom 32B, base64url); persistir só o SHA-256
- [x] Resolução por hash com verificação de expiração/revogação em memória

## API interna (tenant)
- [x] `POST /v1/pendencias/{id}/portal` { o_que_falta, expira_em } → { link }
- [x] `POST /v1/portal/{tokenId}/revogar`
- [x] Atualizar `docs/API.md` com as rotas internas (CLAUDE.md §8)

## API pública (sem login)
- [x] `GET /p/{token}` → projeção pública allowlist (estado grosso, entrada, prazo, o que falta)
- [x] Mapa de status → estado público (em_andamento | concluido)
- [x] Cabeçalho `no-index` (X-Robots-Tag) e `404` uniforme para inválido/expirado/revogado

## Fronteira e segurança
- [x] Projeção é DTO allowlist (nenhum campo interno atravessa)
- [x] Isolamento por token/pendência/tenant (resolução por hash único; join escopado por org)
- [x] `o_que_falta` é texto CURADO (vem do token, nunca derivado do estado interno)
- [x] Token expira junto com o fechamento + folga (`fechada_em` + `portal.folga-fechamento`, default 7d)
- [x] Latência uniforme no 404: uma única consulta (JOIN por hash), decisão em memória

## Testes
- [x] estado público exposto + no-index
- [x] nenhum campo interno na resposta (fronteira ADR-0013 por teste)
- [x] inválido → 404; expirado → 404; revogado → 404
- [x] token não cruza pendência (escopo)
- [x] FECHADA → concluido, sem revelar como
- [x] banco guarda hash (SHA-256), não o token cru
- [x] token expira no fechamento + folga (8 dias → 404)
- [x] o que falta é o texto curado

## Notas
- `POST /p/{token}/autoavaliacao` e conteúdo de **esteira/instância** ficam para o pacote 014.
- Entrega ativa do link à contraparte (por canal) fica para quando houver endereço dela.

---
**Estado:** pacote 007 **completo** — 80 testes JVM, nativo ~71 MB RSS. **F2 fechada**: executor (005),
fechamento ao solicitante (006) e portal à contraparte (007). A contraparte de cada ator existe.
