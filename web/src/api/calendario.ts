import { del, get, post } from "./client";

/** Para onde o provedor devolve o gestor depois do consentimento (RFC-0009). */
export const REDIRECT_CALENDARIO = "/calendario/callback";

/** Chave do `state` anti-CSRF enquanto o gestor está fora, no provedor. */
const CHAVE_STATE = "alcada.calendario.state";

export interface EstadoCalendario {
  conectado: boolean;
  provedor: string | null;
  escopo: string | null;
  /** URL do consentimento, montada pelo servidor (client id + escopo mínimo). */
  urlConsentimento: string | null;
}

export function redirectUri(): string {
  return `${window.location.origin}${REDIRECT_CALENDARIO}`;
}

/**
 * Estado da conta. Pedindo `comConsentimento`, gera e guarda um `state` novo e
 * traz a URL para onde mandar o gestor autorizar.
 */
export function getCalendario(comConsentimento = false): Promise<EstadoCalendario> {
  if (!comConsentimento) {
    return get<EstadoCalendario>("/v1/calendario");
  }
  const state = novoState();
  const q = new URLSearchParams({ redirectUri: redirectUri(), state });
  return get<EstadoCalendario>(`/v1/calendario?${q}`);
}

export function conectarCalendario(codigo: string): Promise<EstadoCalendario> {
  return post<EstadoCalendario>("/v1/calendario", { codigo, redirectUri: redirectUri() });
}

export function revogarCalendario(): Promise<void> {
  return del<void>("/v1/calendario");
}

function novoState(): string {
  const state = crypto.randomUUID();
  sessionStorage.setItem(CHAVE_STATE, state);
  return state;
}

/**
 * Confere o `state` devolvido pelo provedor. Diferente do que guardamos = o
 * retorno não veio do fluxo que este navegador começou: não troca o código.
 */
export function stateConfere(recebido: string | null): boolean {
  const guardado = sessionStorage.getItem(CHAVE_STATE);
  sessionStorage.removeItem(CHAVE_STATE);
  return !!guardado && guardado === recebido;
}
