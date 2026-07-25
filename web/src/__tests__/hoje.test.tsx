import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("../api/pendencias", () => ({
  getEntrada: () => Promise.resolve([]),
  getHoje: () =>
    Promise.resolve(
      Array.from({ length: 5 }, (_, i) => ({
        id: `id-${i}`,
        titulo: `Item ${i}`,
        justificativa: "dinheiro parado",
      })),
    ),
  aplicarSaida: vi.fn(),
  repassar: vi.fn(),
  adiar: vi.fn(),
}));

import { HojePage } from "../components/HojePage";
import { renderComProviders } from "../test/util";

describe("Hoje", () => {
  it("mostra no máximo três itens, cada um com justificativa", async () => {
    renderComProviders(<HojePage />);
    await vi.waitFor(() => expect(screen.getAllByTestId("item-hoje").length).toBeGreaterThan(0));

    const itens = screen.getAllByTestId("item-hoje");
    expect(itens.length).toBe(3);
    itens.forEach((el) => expect(el.textContent).toContain("por quê:"));
  });
});
