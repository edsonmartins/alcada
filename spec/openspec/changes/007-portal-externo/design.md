# Design — 007 portal externo sem login

Referências: `adr/ADR-0013-multi-ator-e-portal-externo.md`, `rfc/RFC-0006-esteira-e-portal-externo.md`.

## Módulos
`notificacao` (emissão/revogação de token + endpoint público de leitura). Reusa `plataforma.trilha`
para registrar emissão/consulta quando fizer sentido. Sem tabela de esteira (é 014).

## Token de portal
```sql
token_portal(
  id, org_id, pendencia_id,
  token_hash text,        -- guarda o HASH do token, nunca o token cru
  o_que_falta text,       -- o que o portal deve mostrar como pendente da contraparte
  expira_em timestamptz,  -- expiração obrigatória
  revogado boolean default false,
  criado_em timestamptz
)
```
- Token é **opaco e aleatório** (alta entropia); persiste-se só o `token_hash` (SHA-256). Comparação
  por hash — vazar o banco não vaza tokens ativos.
- Escopo = uma pendência (`pendencia_id`). Um token nunca enxerga outra pendência.
- Expiração obrigatória; revogação por flag.

## API
```
POST   /v1/pendencias/{id}/portal      { o_que_falta, expira_em }  -> { link }   (interno, tenant)
POST   /v1/portal/{tokenId}/revogar                                              (interno, tenant)
GET    /p/{token}                                                                (público, sem login)
```
`docs/API.md` já lista `GET /p/{token}`; a emissão/revogação internas são acrescentadas ao documento
na implementação (regra do CLAUDE.md §8).

`POST /p/{token}/autoavaliacao` fica para a esteira (014).

## Projeção pública (o que sai por `GET /p/{token}`)
```json
{ "estado": "em_andamento|concluido",
  "entrou_em": "…", "prazo_previsto": "…",
  "o_que_falta": "documento X assinado" }
```
- **Estado é grosso**: mapeia `Pendencia.status` para dois valores públicos —
  `ENTRADA|DELEGADA|AGENDADA|DORMINDO → em_andamento`, `FECHADA → concluido`. Nunca expõe que foi
  *delegada*, para quem, nem a deliberação.
- `prazo_previsto`: prazo da delegação vigente ou `prazo_implicito` da pendência.
- **`o_que_falta` é texto CURADO, não derivado.** É o campo mais útil do portal e o mais perigoso da
  allowlist: se fosse gerado do motivo interno de reprovação, vazaria deliberação ("reprovado porque o
  parceiro não entendeu o modelo" é avaliação interna). Vem **exatamente** do texto que o gestor/admin
  digitou ao emitir o token — nunca do estado interno.

## Ciclo de vida do token contra o ciclo da pendência
O token **não sobrevive para sempre** ao fechamento — URL viva e indexável circulando fora de controle
por tempo indefinido é risco. Quando a pendência fecha, o token expira junto, com **folga curta**
(`portal.folga-fechamento`, default 7 dias): a contraparte vê "concluído" por alguns dias, não para
sempre. Implementação: `pendencia` ganha `fechada_em` (setado nas transições para `FECHADA`); no `GET`,
se `FECHADA`, a expiração efetiva é `min(expira_em, fechada_em + folga)`.

## Fronteira (ADR-0013) — dura e testada
Nunca sai: nome de decisor, status interno detalhado, histórico, dado de outra contraparte, título
cru com conteúdo sensível. A projeção é um DTO **allowlist** — só os quatro campos acima. Um teste
verifica que a resposta não contém `titulo`, `quem_espera`, `classe`, `dono` nem qualquer campo interno.

## Segurança
- `no-index`: cabeçalho `X-Robots-Tag: noindex, nofollow` na resposta pública.
- Token inválido/expirado/revogado → `404` (indistinguível — não confirma existência).
- **Latência uniforme no 404**: inválido, expirado e revogado devem ser indistinguíveis também por
  tempo de resposta — se "revogado" fizer uma consulta a mais que "inválido", dá para separá-los por
  canal lateral de tempo. É link público sem auth; o custo de igualar é baixo. Nota de implementação:
  resolver sempre pela mesma consulta (por hash) e decidir validade em memória, sem ramo com I/O extra.
- Sem autenticação de usuário; o token **é** a credencial, de escopo mínimo e expiração curta.
- Isolamento multi-tenant: a resolução do token traz `org_id` + `pendencia_id`; a leitura é feita só
  sobre aquele par. Um token de um tenant nunca lê pendência de outro.

## Riscos
Token é credencial de portador: expiração curta + revogação + escopo mínimo mitigam. Log de acesso
sem PII. Enumeração de tokens mitigada por alta entropia e resposta `404` uniforme.
