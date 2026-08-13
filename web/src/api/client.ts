import { accessToken, orgId, pessoaId } from "./config";
import { ProblemaError } from "./types";

async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const bearer = accessToken();
  const res = await fetch(path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      "X-Org-Id": orgId(),
      "X-Pessoa-Id": pessoaId(),
      // Ponte: no %demo o backend usa os headers; em %prod lê do Bearer.
      ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}),
      ...(init?.headers ?? {}),
    },
  });

  if (!res.ok) {
    if (res.headers.get("content-type")?.includes("problem+json")) {
      const p = await res.json();
      throw new ProblemaError(res.status, p.type ?? "", p.detail ?? res.statusText);
    }
    throw new ProblemaError(res.status, "urn:alcada:erro", res.statusText);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

export function get<T>(path: string): Promise<T> {
  return apiFetch<T>(path);
}

export function post<T>(path: string, body?: unknown): Promise<T> {
  return apiFetch<T>(path, { method: "POST", body: body ? JSON.stringify(body) : undefined });
}

export function postIdempotente<T>(path: string, body: unknown, chave: string): Promise<T> {
  return apiFetch<T>(path, {
    method: "POST",
    headers: { "Idempotency-Key": chave },
    body: JSON.stringify(body),
  });
}

export function put<T>(path: string, body?: unknown): Promise<T> {
  return apiFetch<T>(path, { method: "PUT", body: body ? JSON.stringify(body) : undefined });
}

export function del<T>(path: string): Promise<T> {
  return apiFetch<T>(path, { method: "DELETE" });
}

/** Chamadas administrativas do piloto; em produção o papel vem do token. */
export function getAdmin<T>(path: string): Promise<T> {
  return apiFetch<T>(path, { headers: { "X-Alcada-Papel": "ADMIN" } });
}

export function postAdmin<T>(path: string, body: unknown): Promise<T> {
  return apiFetch<T>(path, {
    method: "POST",
    headers: { "X-Alcada-Papel": "ADMIN" },
    body: JSON.stringify(body),
  });
}
