import type { IdTokenClaims } from "oidc-client-ts";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { get } from "../api/client";
import { sessaoDoPerfil } from "../api/oidc";

const perfil = (extra: Record<string, unknown>): IdTokenClaims =>
  ({ sub: "s", iss: "i", aud: "a", exp: 0, iat: 0, ...extra }) as unknown as IdTokenClaims;

describe("sessaoDoPerfil (claims → tenant)", () => {
  it("extrai org_id/pessoa_id e o rótulo do id_token", () => {
    const d = sessaoDoPerfil(
      perfil({ org_id: "O", pessoa_id: "P", name: "Gestor Piloto" }),
    );
    expect(d).toEqual({ org: "O", pessoa: "P", rotulo: "Gestor Piloto" });
  });

  it("cai no preferred_username quando não há name", () => {
    const d = sessaoDoPerfil(perfil({ org_id: "O", pessoa_id: "P", preferred_username: "gestor.piloto" }));
    expect(d.rotulo).toBe("gestor.piloto");
  });

  it("lança quando o token não traz org_id/pessoa_id", () => {
    expect(() => sessaoDoPerfil(perfil({ name: "x" }))).toThrow(/org_id/);
  });
});

describe("apiFetch Bearer (ponte)", () => {
  beforeEach(() => localStorage.clear());

  function capturarFetch(): { headers: () => Record<string, string> } {
    let cap: Record<string, string> = {};
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url: string, init: RequestInit) => {
        cap = init.headers as Record<string, string>;
        return {
          ok: true,
          status: 200,
          headers: new Headers(),
          json: async () => ({}),
        } as Response;
      }),
    );
    return { headers: () => cap };
  }

  it("com accessToken: envia Authorization e os headers de tenant", async () => {
    localStorage.setItem("alcada.orgId", "O");
    localStorage.setItem("alcada.pessoaId", "P");
    localStorage.setItem("alcada.accessToken", "TOK");
    const f = capturarFetch();
    await get("/x");
    expect(f.headers().Authorization).toBe("Bearer TOK");
    expect(f.headers()["X-Org-Id"]).toBe("O");
    expect(f.headers()["X-Pessoa-Id"]).toBe("P");
    vi.unstubAllGlobals();
  });

  it("sem accessToken: não envia Authorization (fallback do piloto)", async () => {
    localStorage.setItem("alcada.orgId", "O");
    localStorage.setItem("alcada.pessoaId", "P");
    const f = capturarFetch();
    await get("/x");
    expect(f.headers().Authorization).toBeUndefined();
    expect(f.headers()["X-Org-Id"]).toBe("O");
    vi.unstubAllGlobals();
  });
});
