import { fireEvent, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("../api/pendencias", () => ({
  getEntrada: () =>
    Promise.resolve([
      { id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", titulo: "Item de hoje", classe: "DECISAO", horizonte: "HOJE", status: "ENTRADA", quemEspera: null, temperatura: 0, baixaConfianca: false, oQueTrava: null, valorEmJogo: null, prazoImplicito: null, criadaEm: null },
      { id: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", titulo: "Bloqueio da semana", classe: "BLOQUEIO", horizonte: "SEMANA", status: "ENTRADA", quemEspera: null, temperatura: 0, baixaConfianca: false, oQueTrava: null, valorEmJogo: null, prazoImplicito: null, criadaEm: null },
      { id: "cccccccc-cccc-cccc-cccc-cccccccccccc", titulo: "Decisão do trimestre", classe: "DECISAO", horizonte: "TRIMESTRE", status: "ENTRADA", quemEspera: null, temperatura: 0, baixaConfianca: false, oQueTrava: null, valorEmJogo: null, prazoImplicito: null, criadaEm: null },
    ]),
  getHoje: () => Promise.resolve([]),
  aplicarSaida: vi.fn(),
  repassar: vi.fn(),
  adiar: vi.fn(),
}));

import { EntradaPage } from "../components/EntradaPage";
import { renderComProviders, resetUI } from "../test/util";

describe("filtros da entrada", () => {
  it("filtra por horizonte e por classe", async () => {
    resetUI();
    renderComProviders(<EntradaPage />);
    await screen.findByText("Item de hoje");
    expect(screen.getByText("Bloqueio da semana")).toBeInTheDocument();
    expect(screen.getByText("Decisão do trimestre")).toBeInTheDocument();

    // horizonte "Semana" → só o bloqueio da semana
    fireEvent.click(screen.getByText("Semana"));
    expect(screen.queryByText("Item de hoje")).not.toBeInTheDocument();
    expect(screen.getByText("Bloqueio da semana")).toBeInTheDocument();
    expect(screen.queryByText("Decisão do trimestre")).not.toBeInTheDocument();

    // volta para todos os horizontes, filtra classe "Decisão" → hoje + trimestre
    fireEvent.click(screen.getByText("Todos"));
    fireEvent.click(screen.getByText("Decisão"));
    expect(screen.getByText("Item de hoje")).toBeInTheDocument();
    expect(screen.getByText("Decisão do trimestre")).toBeInTheDocument();
    expect(screen.queryByText("Bloqueio da semana")).not.toBeInTheDocument();
  });
});
