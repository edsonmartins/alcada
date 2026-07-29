import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { pendencia } from "../test/util";

vi.mock("../api/pendencias", () => ({
  getEntrada: () =>
    Promise.resolve([
      pendencia({
        id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        titulo: "Reunião de cronograma",
        quemEspera: "Marcello",
        origemGrupo: "Projeto Rio",
        cobrancas: 3,
      }),
    ]),
  getHoje: () => Promise.resolve([]),
  aplicarSaida: vi.fn(),
  repassar: vi.fn(),
  adiar: vi.fn(),
}));

import { EntradaPage } from "../components/EntradaPage";
import { renderComProviders, resetUI } from "../test/util";

describe("origem de grupo na Entrada (024)", () => {
  it("mostra o grupo, o dono por 1º nome e quantas vezes já cobraram", async () => {
    resetUI();
    renderComProviders(<EntradaPage />);
    await screen.findByText("Reunião de cronograma");
    expect(screen.getByText("grupo: Projeto Rio")).toBeInTheDocument();
    expect(screen.getByText("espera: Marcello")).toBeInTheDocument();
    expect(screen.getByText("já te cobraram 3×")).toBeInTheDocument();
  });
});
