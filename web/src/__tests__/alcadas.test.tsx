import { fireEvent, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { PropostaRegra, RegraAtiva } from "../api/regras";

const PROPOSTAS: PropostaRegra[] = [
  {
    classe: "DECISAO",
    ocorrencias: 18,
    consistencia: 1,
    nivelSugerido: "N1",
    donoSugerido: "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
    casos: [
      { pendenciaId: "p1", titulo: "Reembolso Rafael", desfecho: "RESOLVIDA", valorEmJogo: 1200 },
    ],
  },
];
const ATIVAS: RegraAtiva[] = [];

const h = vi.hoisted(() => ({
  criarRegra: vi.fn().mockResolvedValue({ id: "r1" }),
  silenciar: vi.fn().mockResolvedValue(undefined),
}));

vi.mock("../api/regras", async () => {
  const real = await vi.importActual<typeof import("../api/regras")>("../api/regras");
  return {
    ...real,
    getPropostas: () => Promise.resolve(PROPOSTAS),
    getRegras: () => Promise.resolve(ATIVAS),
    criarRegra: h.criarRegra,
    silenciar: h.silenciar,
  };
});

import { AlcadasPage } from "../components/AlcadasPage";
import { renderComProviders } from "../test/util";

describe("alçadas — mineração de regras", () => {
  it("mostra a proposta com evidência e aceita com nível/dono sugeridos", async () => {
    renderComProviders(<AlcadasPage />);
    await screen.findByText(/18 decisões/);
    // evidência disponível
    expect(screen.getByText(/Ver evidência \(1 casos\)/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Aceitar" }));
    await waitFor(() =>
      expect(h.criarRegra).toHaveBeenCalledWith({
        classe: "DECISAO",
        nivel: "N1",
        donoId: "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
      }),
    );
  });

  it("silencia a classe", async () => {
    renderComProviders(<AlcadasPage />);
    await screen.findByText(/18 decisões/);
    fireEvent.click(screen.getByRole("button", { name: "Silenciar" }));
    await waitFor(() => expect(h.silenciar).toHaveBeenCalledWith("DECISAO"));
  });
});
