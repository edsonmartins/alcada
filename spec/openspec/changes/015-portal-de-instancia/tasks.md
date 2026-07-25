# Tasks — 015 portal de instância + autoavaliação

## Migration
- [ ] `V17__portal_instancia.sql`: token_instancia + autoavaliacao; GuardaOrgId conhece as tabelas

## Backend — módulo esteira (portal)
- [ ] `esteira.port.PortalInstancia` + DTOs (EstadoInstancia, TokenEmitido, Declaracao)
- [ ] `PortalInstanciaJdbc`: emitir (hash), revogar, resolver (projeção curada; empty uniforme),
      autoavaliar; prazo = entrada + sla; o que falta = critérios OBJETIVOS da etapa do gestor

## Backend — API
- [ ] `POST /v1/instancias/{id}/portal`; `POST /v1/instancias/portais/{tokenId}/revogar` (gestor)
- [ ] `GET /pi/{token}`; `POST /pi/{token}/autoavaliacao` (público; noindex; sem contexto por header)
- [ ] `@RegisterForReflection`; problem+json; docs/API.md

## Web — página pública
- [ ] Rota `/portal/instancia/$token` isenta do guard de sessão (Layout exime /portal/*)
- [ ] Estado + prazo + "o que falta" + formulário de autoavaliação
- [ ] Botão "gerar link do portal" na tela da esteira (instância)

## Testes
- [ ] Backend: estado curado; token uniforme p/ inválido/expirado/revogado; autoavaliação grava;
      emissão só-hash; revogação corta; isolamento
- [ ] Web (Vitest): página pública renderiza estado + envia autoavaliação

## Verificação
- [ ] JVM suite verde + build nativo + RSS ≤120 MB
- [ ] Deploy no piloto (GHCR pull) e conferência do portal público
