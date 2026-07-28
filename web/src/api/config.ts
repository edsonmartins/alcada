/**
 * Contexto de sessão. Em produção vem do token OIDC (org_id na claim; pessoa no
 * `sub`). No piloto (profile demo, sem IdP) vem do localStorage, definido na
 * tela de sessão (/entrar). A API valida o tenant de qualquer forma (INV-15).
 */
const CHAVE_ORG = "alcada.orgId";
const CHAVE_PESSOA = "alcada.pessoaId";
const CHAVE_ROTULO = "alcada.rotulo"; // nome amigável, só para exibir
const CHAVE_TOKEN = "alcada.accessToken"; // Bearer OIDC (quando logado via ArchGuard)

export function orgId(): string {
  return localStorage.getItem(CHAVE_ORG) ?? "";
}

export function pessoaId(): string {
  return localStorage.getItem(CHAVE_PESSOA) ?? "";
}

/**
 * Access token OIDC. Quando presente, vai como `Authorization: Bearer` junto com
 * os headers de tenant: no piloto (%demo, OIDC off) o backend usa os headers; em
 * %prod ele lê org_id/pessoa_id do próprio Bearer. Vazio = acesso manual/offline.
 */
export function accessToken(): string {
  return localStorage.getItem(CHAVE_TOKEN) ?? "";
}

export function definirAccessToken(token: string | undefined): void {
  if (token && token.trim()) {
    localStorage.setItem(CHAVE_TOKEN, token);
  } else {
    localStorage.removeItem(CHAVE_TOKEN);
  }
}

export function rotuloSessao(): string {
  return localStorage.getItem(CHAVE_ROTULO) ?? "";
}

export function temSessao(): boolean {
  return orgId().trim() !== "" && pessoaId().trim() !== "";
}

/** UUID canônico (8-4-4-4-12), case-insensitive. */
export const RE_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Remove espaços e aspas que costumam vir coladas ao copiar de um `.env`
 * (o padrão da casa guarda valores entre aspas: ALCADA_ORG_ID="…"). Sem isto o
 * header vira um UUID inválido e a API responde 500 ("UUID string too large").
 */
export function limparId(v: string): string {
  return (v ?? "").trim().replace(/^["']+|["']+$/g, "").trim();
}

export function definirSessao(org: string, pessoa: string, rotulo?: string): void {
  localStorage.setItem(CHAVE_ORG, limparId(org));
  localStorage.setItem(CHAVE_PESSOA, limparId(pessoa));
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
  localStorage.removeItem(CHAVE_TOKEN);
}
