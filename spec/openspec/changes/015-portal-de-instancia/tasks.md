# Tasks — 015 portal de instância + autoavaliação

## Migration
- [x] `V17__portal_instancia.sql`: token_instancia + autoavaliacao; GuardaOrgId (token_instancia em
      FILAS_TENANT — resolvido pelo token, como a fonte; autoavaliacao em DADOS_TENANT)

## Backend — módulo esteira (portal)
- [x] `esteira.port.PortalInstancia` + DTOs (EstadoInstancia/ItemFalta, TokenEmitido, Declaracao)
- [x] `PortalInstanciaJdbc`: emitir (hash), revogar, resolver (projeção curada; empty uniforme),
      autoavaliar; prazo = entrada + sla; o que falta = critérios OBJETIVOS da etapa do gestor

## Backend — API
- [x] `POST /v1/instancias/{id}/portal`; `POST /v1/instancias/portais/{tokenId}/revogar` (gestor)
- [x] `GET /pi/{token}`; `POST /pi/{token}/autoavaliacao` (público; noindex; sem contexto por header)
- [x] `@RegisterForReflection`; problem+json; docs/API.md

## Web — página pública
- [x] Rota `/portal/instancia/$token` isenta do guard de sessão (Layout: portalPublico → só Outlet)
- [x] Estado + prazo + "o que falta" + formulário de autoavaliação
- [x] Botão "Portal" (gerar link) na tela da esteira (instância)

## Testes
- [x] Backend: estado curado; token uniforme p/ inválido/expirado/revogado; autoavaliação grava;
      emissão só-hash; revogação corta; isolamento — PortalInstanciaTest (7)
- [x] Web (Vitest): página pública renderiza estado + envia autoavaliação — portal-instancia.test.tsx

## Verificação
- [x] JVM suite verde (136) + 33 Vitest
- [ ] build nativo + RSS ≤120 MB
- [ ] Deploy no piloto (GHCR pull) e conferência do portal público
