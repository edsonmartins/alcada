import { act, fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const h = vi.hoisted(() => ({ aplicarSaida: vi.fn().mockResolvedValue(undefined) }));

vi.mock("../api/pendencias", () => ({
  getEntrada: () =>
    Promise.resolve([
      { id: "11111111-1111-1111-1111-111111111111", titulo: "Primeiro", classe: "DECISAO", horizonte: "SEMANA", status: "ENTRADA", quemEspera: null, temperatura: 0, baixaConfianca: false },
      { id: "22222222-2222-2222-2222-222222222222", titulo: "Segundo", classe: "DECISAO", horizonte: "SEMANA", status: "ENTRADA", quemEspera: null, temperatura: 0, baixaConfianca: false },
    ]),
  getHoje: () => Promise.resolve([]),
  aplicarSaida: h.aplicarSaida,
  repassar: vi.fn(),
  adiar: vi.fn(),
}));

import { EntradaPage } from "../components/EntradaPage";
import { useUI } from "../store/ui";
import { renderComProviders, resetUI } from "../test/util";

describe("triagem inteira pelo teclado", () => {
  beforeEach(() => {
    resetUI();
    vi.useFakeTimers();
    h.aplicarSaida.mockClear();
  });
  afterEach(() => vi.useRealTimers());

  it("tecla 1 resolve o item sob o cursor após a janela de desfazer", async () => {
    renderComProviders(<EntradaPage />);
    await vi.waitFor(() => expect(screen.getByText("Primeiro")).toBeInTheDocument());

    fireEvent.keyDown(window, { key: "1" });
    // otimista: some da lista, mas o efeito ainda não saiu
    expect(h.aplicarSaida).not.toHaveBeenCalled();

    await act(async () => {
      vi.advanceTimersByTime(5000);
    });
    expect(h.aplicarSaida).toHaveBeenCalledWith("11111111-1111-1111-1111-111111111111", "resolver");
  });

  it("j move o cursor; 1 resolve o segundo item", async () => {
    renderComProviders(<EntradaPage />);
    await vi.waitFor(() => expect(screen.getByText("Segundo")).toBeInTheDocument());

    fireEvent.keyDown(window, { key: "j" });
    fireEvent.keyDown(window, { key: "1" });
    await act(async () => {
      vi.advanceTimersByTime(5000);
    });
    expect(h.aplicarSaida).toHaveBeenCalledWith("22222222-2222-2222-2222-222222222222", "resolver");
  });

  it("a abre o adiar como formulário (ação secundária)", async () => {
    vi.useRealTimers(); // o Drawer do Mantine usa transição por timer
    renderComProviders(<EntradaPage />);
    await screen.findByText("Primeiro");

    fireEvent.keyDown(window, { key: "a" });
    expect(useUI.getState().form).toBe("adiar");
    await screen.findByText(/ausência de decisão/i);
  });
});
