import { fireEvent, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { EstadoCalendario } from "../api/calendario";

const h = vi.hoisted(() => ({
  estado: {
    conectado: false,
    provedor: null,
    escopo: null,
    urlConsentimento: "https://accounts.google.com/o/oauth2/v2/auth?x=1",
  } as EstadoCalendario,
  revogar: vi.fn().mockResolvedValue(undefined),
  conectar: vi.fn().mockResolvedValue({ conectado: true }),
  confere: vi.fn().mockReturnValue(true),
}));

vi.mock("../api/calendario", async () => {
  const real = await vi.importActual<typeof import("../api/calendario")>("../api/calendario");
  return {
    ...real,
    getCalendario: () => Promise.resolve(h.estado),
    revogarCalendario: h.revogar,
    conectarCalendario: h.conectar,
    stateConfere: h.confere,
  };
});

vi.mock("../api/contatos", async () => {
  const real = await vi.importActual<typeof import("../api/contatos")>("../api/contatos");
  return { ...real, getContatos: () => Promise.resolve([]) };
});

vi.mock("../api/fontes", async () => {
  const real = await vi.importActual<typeof import("../api/fontes")>("../api/fontes");
  return { ...real, getFontes: () => Promise.resolve([]) };
});

const navegou = vi.hoisted(() => vi.fn());
vi.mock("@tanstack/react-router", () => ({ useNavigate: () => navegou }));

import { CalendarioCallbackPage } from "../components/CalendarioCallbackPage";
import { CanaisPage } from "../components/CanaisPage";
import { renderComProviders } from "../test/util";

function comBusca(busca: string) {
  window.history.replaceState({}, "", `/calendario/callback${busca}`);
}

describe("calendário do gestor (RFC-0009 F2.5)", () => {
  beforeEach(() => {
    h.revogar.mockClear();
    h.conectar.mockClear();
    h.confere.mockReturnValue(true);
  });

  // C16 — desconectado: oferece o link de consentimento montado pelo servidor
  it("oferece conectar quando não há conta", async () => {
    h.estado = { conectado: false, provedor: null, escopo: null,
      urlConsentimento: "https://accounts.google.com/o/oauth2/v2/auth?x=1" };
    renderComProviders(<CanaisPage />);

    const botao = await screen.findByRole("link", { name: /Conectar meu Google Agenda/ });
    expect(botao).toHaveAttribute("href", "https://accounts.google.com/o/oauth2/v2/auth?x=1");
  });

  // C16 — conectado: mostra o provedor e desconecta
  it("mostra a conta conectada e permite desconectar", async () => {
    h.estado = { conectado: true, provedor: "GOOGLE", escopo: "events", urlConsentimento: null };
    renderComProviders(<CanaisPage />);

    await screen.findByText(/Conectado ao Google Agenda/);
    fireEvent.click(screen.getByRole("button", { name: "Desconectar" }));
    await waitFor(() => expect(h.revogar).toHaveBeenCalled());
  });

  // Ambiente sem integração configurada: avisa em vez de oferecer botão morto
  it("avisa quando o ambiente não tem calendário configurado", async () => {
    h.estado = { conectado: false, provedor: null, escopo: null, urlConsentimento: null };
    renderComProviders(<CanaisPage />);

    await screen.findByText(/não configurada neste ambiente/);
  });

  // C16 — retorno do provedor: troca o código
  it("callback troca o código do provedor por tokens", async () => {
    comBusca("?code=abc123&state=s1");
    renderComProviders(<CalendarioCallbackPage />);

    await waitFor(() => expect(h.conectar).toHaveBeenCalledWith("abc123"));
    await screen.findByText(/Agenda conectada/);
  });

  // Anti-CSRF: retorno que não casa com o pedido deste navegador não troca nada
  it("callback recusa quando o state não confere", async () => {
    h.confere.mockReturnValue(false);
    comBusca("?code=abc123&state=outro");
    renderComProviders(<CalendarioCallbackPage />);

    await screen.findByText(/não confere com o pedido/);
    expect(h.conectar).not.toHaveBeenCalled();
  });

  // O gestor pode recusar no Google — a tela diz isso, sem parecer erro do sistema
  it("callback explica quando o gestor não autoriza", async () => {
    comBusca("?error=access_denied");
    renderComProviders(<CalendarioCallbackPage />);

    await screen.findByText(/não autorizou o acesso/);
    expect(h.conectar).not.toHaveBeenCalled();
  });
});
