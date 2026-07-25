import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../api/pendencias", () => ({
  getEntrada: () =>
    Promise.resolve([
      { id: "11111111-1111-1111-1111-111111111111", titulo: "Item A", classe: "DECISAO", horizonte: "SEMANA", status: "ENTRADA", quemEspera: null, temperatura: 0, baixaConfianca: false },
      { id: "22222222-2222-2222-2222-222222222222", titulo: "Item B", classe: "DECISAO", horizonte: "SEMANA", status: "ENTRADA", quemEspera: null, temperatura: 0, baixaConfianca: false },
    ]),
  getHoje: () => Promise.resolve([]),
  aplicarSaida: vi.fn(),
  repassar: vi.fn(),
  adiar: vi.fn(),
}));

import { EntradaPage } from "../components/EntradaPage";
import { renderComProviders, resetUI } from "../test/util";

describe("anti-jardinagem (ADR-0018): não existe arrastar", () => {
  beforeEach(() => resetUI());

  it("nenhum elemento é arrastável", async () => {
    const { container } = renderComProviders(<EntradaPage />);
    await vi.waitFor(() => expect(screen.getByText("Item A")).toBeInTheDocument());

    expect(container.querySelectorAll('[draggable="true"]').length).toBe(0);
    // e nenhum elemento declara o atributo draggable de forma alguma
    expect(container.querySelectorAll("[draggable]").length).toBe(0);
  });

  it("não há biblioteca de drag-and-drop nas dependências", async () => {
    const pkg = (await import("../../package.json")).default as { dependencies: Record<string, string> };
    const proibidas = Object.keys(pkg.dependencies).filter((d) =>
      /dnd|draggable|sortable|dropzone/i.test(d),
    );
    expect(proibidas).toEqual([]);
  });
});
