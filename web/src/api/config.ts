/**
 * Contexto de sessão. Em produção vem do token OIDC (org_id na claim; pessoa no
 * `sub`). Nesta fase, dev usa localStorage; a API valida o tenant de qualquer forma.
 */
export function orgId(): string {
  return localStorage.getItem("alcada.orgId") ?? "";
}

export function pessoaId(): string {
  return localStorage.getItem("alcada.pessoaId") ?? "";
}
