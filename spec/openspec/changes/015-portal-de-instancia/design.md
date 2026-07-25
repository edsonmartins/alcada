# Design — 015 portal de instância + autoavaliação

## Migration V17
```
token_instancia (
  id uuid pk, org_id uuid not null, instancia_id uuid not null references instancia(id),
  token_hash text not null unique, expira_em timestamptz not null,
  revogado boolean not null default false, criado_em timestamptz default now())
autoavaliacao (
  id uuid pk, org_id uuid not null, instancia_id uuid not null,
  criterio_chave text not null, conforme boolean not null, declarado_em timestamptz default now())
```
Ambas em DADOS_TENANT do GuardaOrgId. Token: guarda só o hash (SHA-256), nunca o token cru.

## Módulo esteira (portal)
`esteira.port.PortalInstancia`:
- `TokenEmitido emitir(org, instanciaId, expiraEm)` — devolve o token cru UMA vez, persiste hash.
- `boolean revogar(org, tokenId)`
- `Optional<EstadoInstancia> resolver(tokenCru)` — resolve por hash; empty uniforme p/ inválido/
  expirado/revogado.
- `void autoavaliar(tokenCru, List<Declaracao>)` — resolve o token → instancia/org, grava declarações.

`EstadoInstancia` (projeção pública curada):
- esteiraNome, etapaAtualNome, entrouEm, prazoPrevisto (= entrouEm + sla da etapa, se houver),
  oQueFalta: List<Criterio OBJETIVO da etapa do gestor> (chave, descricao).
- NUNCA: dono/decisores, avaliações internas, outras instâncias.

## Endpoints
```
POST /v1/instancias/{id}/portal                 -> { token } (gestor; token cru uma vez)
POST /v1/instancias/portais/{tokenId}/revogar   -> 204
GET  /pi/{token}                                 -> EstadoInstancia | 404 uniforme (público)
POST /pi/{token}/autoavaliacao  { declaracoes:[{criterioChave, conforme}] }  -> 204 (público)
```
`GET /pi` e `POST /pi/.../autoavaliacao` NÃO passam pelo contexto de tenant por header — o token
resolve o org (como o /p do 007). Cabeçalhos `X-Robots-Tag: noindex`.

## Web — página pública
Rota `/portal/instancia/$token` **isenta do guard de sessão** (o Layout redireciona a /entrar
apenas quando não é /entrar nem /portal/*). Mostra estado + prazo + "o que falta" + formulário de
autoavaliação (checkbox por critério). Sem nav interna, sem dados internos.

## Segurança / privacidade
- Token = credencial; só hash no banco; resposta uniforme p/ inválido.
- Projeção curada (nada interno). `noindex`. INV-15 pelo token.
- Autoavaliação **informa** o gestor (aparece na avaliação da instância); não decide (INV-10).

## Fora do design
- Upload de anexos; notificação automática; reavaliação automática pela autoavaliação.
