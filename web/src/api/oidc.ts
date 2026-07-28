import {
  type IdTokenClaims,
  type User,
  UserManager,
  WebStorageStateStore,
} from "oidc-client-ts";
import { definirAccessToken, definirSessao, limparSessao } from "./config";

/**
 * Login OIDC (authorization_code + PKCE) contra o ArchGuard / Casdoor. O org_id e
 * o pessoa_id saem das claims custom do id_token e alimentam a resolução de tenant
 * — no piloto (%demo) via headers X-Org-Id/X-Pessoa-Id; em %prod o backend lê do
 * próprio Bearer. Config sobrescrevível por variável de build (VITE_OIDC_*).
 */
const authority: string =
  import.meta.env.VITE_OIDC_AUTHORITY || "https://app.archguard.com.br";
const clientId: string =
  import.meta.env.VITE_OIDC_CLIENT_ID || "b93137e05e41986ccce3";

export const oidcHabilitado = Boolean(authority && clientId);

let _mgr: UserManager | null = null;
function mgr(): UserManager {
  if (!_mgr) {
    _mgr = new UserManager({
      authority,
      client_id: clientId,
      redirect_uri: `${window.location.origin}/callback`,
      post_logout_redirect_uri: window.location.origin,
      scope: "openid profile email offline_access",
      userStore: new WebStorageStateStore({ store: window.localStorage }),
      automaticSilentRenew: true,
    });
  }
  return _mgr;
}

export type DadosSessao = { org: string; pessoa: string; rotulo: string };

/**
 * Extrai org_id/pessoa_id (e um rótulo amigável) das claims do id_token. Parte
 * pura e testável — a validação de assinatura é do backend (via JWKS). Lança se o
 * token não trouxer as claims de tenant.
 */
export function sessaoDoPerfil(profile: IdTokenClaims): DadosSessao {
  const p = profile as Record<string, unknown>;
  const org = String(p.org_id ?? "").trim();
  const pessoa = String(p.pessoa_id ?? "").trim();
  if (!org || !pessoa) {
    throw new Error(
      "o token não trouxe org_id/pessoa_id — confira as claims no ArchGuard",
    );
  }
  const rotulo = String(profile.name ?? profile.preferred_username ?? "").trim();
  return { org, pessoa, rotulo };
}

function aplicar(user: User): void {
  const { org, pessoa, rotulo } = sessaoDoPerfil(user.profile);
  definirSessao(org, pessoa, rotulo);
  definirAccessToken(user.access_token);
}

/** Redireciona o navegador para o login do ArchGuard. */
export function entrarOidc(): Promise<void> {
  return mgr().signinRedirect();
}

/** Trata o retorno do IdP (rota /callback): troca o code por tokens e aplica a sessão. */
export async function tratarCallback(): Promise<void> {
  const user = await mgr().signinRedirectCallback();
  aplicar(user);
}

/** Restaura uma sessão OIDC salva (renova em silêncio se expirou). `true` se aplicou. */
export async function restaurarOidc(): Promise<boolean> {
  try {
    let user = await mgr().getUser();
    if (user?.expired) {
      user = await mgr().signinSilent();
    }
    if (user && !user.expired) {
      aplicar(user);
      return true;
    }
  } catch {
    // sem sessão OIDC utilizável — o app cai na tela de sessão
  }
  return false;
}

/** Limpa a sessão local (tenant + token). O logout no IdP é opcional no piloto. */
export async function sairOidc(): Promise<void> {
  limparSessao();
  try {
    await mgr().removeUser();
  } catch {
    // sem manager/sessão: ok
  }
}
