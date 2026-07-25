/**
 * Contexto de sessão. Em produção vem do token OIDC (org_id na claim; pessoa no
 * `sub`). No piloto (profile demo, sem IdP) vem do localStorage, definido na
 * tela de sessão (/entrar). A API valida o tenant de qualquer forma (INV-15).
 */
const CHAVE_ORG = "alcada.orgId";
const CHAVE_PESSOA = "alcada.pessoaId";
const CHAVE_ROTULO = "alcada.rotulo"; // nome amigável, só para exibir

export function orgId(): string {
  return localStorage.getItem(CHAVE_ORG) ?? "";
}

export function pessoaId(): string {
  return localStorage.getItem(CHAVE_PESSOA) ?? "";
}

export function rotuloSessao(): string {
  return localStorage.getItem(CHAVE_ROTULO) ?? "";
}

export function temSessao(): boolean {
  return orgId().trim() !== "" && pessoaId().trim() !== "";
}

export function definirSessao(org: string, pessoa: string, rotulo?: string): void {
  localStorage.setItem(CHAVE_ORG, org.trim());
  localStorage.setItem(CHAVE_PESSOA, pessoa.trim());
  if (rotulo && rotulo.trim()) {
    localStorage.setItem(CHAVE_ROTULO, rotulo.trim());
  } else {
    localStorage.removeItem(CHAVE_ROTULO);
  }
}

export function limparSessao(): void {
  localStorage.removeItem(CHAVE_ORG);
  localStorage.removeItem(CHAVE_PESSOA);
  localStorage.removeItem(CHAVE_ROTULO);
}
