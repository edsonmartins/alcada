import { fireEvent, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { PerguntaAprendizado } from "../api/aprendizado";

const PERGUNTAS: PerguntaAprendizado[] = [
  {
    id: "q1",
    classe: "DECISAO",
    nivelSugerido: "N1",
    donoSugerido: "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
    ocorrencias: 12,
    casos: [{ pendenciaId: "p1", titulo: "Reembolso Rafael", desfecho: "RESOLVIDA", valorEmJogo: 1200 }],
  },
];

const h = vi.hoisted(() => ({ responder: vi.fn().mockResolvedValue(undefined) }));

vi.mock("../api/aprendizado", async () => {
  const real = await vi.importActual<typeof import("../api/aprendizado")>("../api/aprendizado");
  return { ...real, getPerguntas: () => Promise.resolve(PERGUNTAS), responder: h.responder };
});
vi.mock("../api/pendencias", () => ({
  getHoje: () => Promise.resolve([]),
  getEntrada: () => Promise.resolve([]),
  aplicarSaida: vi.fn(),
  repassar: vi.fn(),
  adiar: vi.fn(),
}));

import { HojePage } from "../components/HojePage";
import { renderComProviders } from "../test/util";

describe("laço de aprendizado em /hoje", () => {
  it("mostra a pergunta com evidência e as três respostas", async () => {
    renderComProviders(<HojePage />);
    await screen.findByTestId("pergunta-aprendizado");
    expect(screen.getByText(/12 casos/)).toBeInTheDocument();
    expect(screen.getByText(/Ver evidência \(1 casos\)/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sim, criar regra" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Agora não" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Não perguntar isso" })).toBeInTheDocument();
  });

  it("responder 'sim' chama a API", async () => {
    renderComProviders(<HojePage />);
    await screen.findByTestId("pergunta-aprendizado");
    fireEvent.click(screen.getByRole("button", { name: "Sim, criar regra" }));
    await waitFor(() => expect(h.responder).toHaveBeenCalledWith("q1", "SIM"));
  });
});
