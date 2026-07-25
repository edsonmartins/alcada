import { act, fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const h = vi.hoisted(() => ({ aplicarSaida: vi.fn().mockResolvedValue(undefined) }));

vi.mock("../api/pendencias", () => ({
  getEntrada: () =>
    Promise.resolve([
      { id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", titulo: "Única", classe: "DECISAO", horizonte: "SEMANA", status: "ENTRADA", quemEspera: null, temperatura: 0, baixaConfianca: false },
    ]),
  getHoje: () => Promise.resolve([]),
  aplicarSaida: h.aplicarSaida,
  repassar: vi.fn(),
  adiar: vi.fn(),
}));

import { EntradaPage } from "../components/EntradaPage";
import { renderComProviders, resetUI } from "../test/util";

describe("janela de desfazer (INV-14)", () => {
  beforeEach(() => {
    resetUI();
    vi.useFakeTimers();
    h.aplicarSaida.mockClear();
  });
  afterEach(() => vi.useRealTimers());

  it("desfazer dentro da janela reverte e nenhum efeito externo é enviado", async () => {
    renderComProviders(<EntradaPage />);
    await vi.waitFor(() => expect(screen.getByText("Única")).toBeInTheDocument());

    fireEvent.keyDown(window, { key: "1" }); // resolver (otimista)
    expect(screen.queryByText("Única")).not.toBeInTheDocument(); // sumiu da lista
    expect(screen.getByTestId("desfazer-barra")).toBeInTheDocument();

    fireEvent.click(screen.getByText("Desfazer"));
    expect(screen.getByText("Única")).toBeInTheDocument(); // voltou

    await act(async () => {
      vi.advanceTimersByTime(6000);
    });
    expect(h.aplicarSaida).not.toHaveBeenCalled(); // nada saiu para fora
  });

  it("sem desfazer, o efeito é enviado ao fim da janela", async () => {
    renderComProviders(<EntradaPage />);
    await vi.waitFor(() => expect(screen.getByText("Única")).toBeInTheDocument());

    fireEvent.keyDown(window, { key: "1" });
    await act(async () => {
      vi.advanceTimersByTime(5000);
    });
    expect(h.aplicarSaida).toHaveBeenCalledWith("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "resolver");
  });
});
