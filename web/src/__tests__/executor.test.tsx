import { fireEvent, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const h = vi.hoisted(() => ({
  concluir: vi.fn().mockResolvedValue(undefined),
  devolver: vi.fn().mockResolvedValue(undefined),
  propor: vi.fn().mockResolvedValue(undefined),
}));

vi.mock("../api/delegacoes", async () => {
  const real = await vi.importActual<typeof import("../api/delegacoes")>("../api/delegacoes");
  return {
    ...real,
    getMinhasDelegacoes: () =>
      Promise.resolve([
        {
          id: "dddddddd-dddd-dddd-dddd-dddddddddddd",
          pendenciaId: "pppppppp-pppp-pppp-pppp-pppppppppppp",
          donoId: "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
          nivel: "N2",
          status: "PROPOSTA",
          proposta: "reajuste de 4,2%",
          prazo: "2026-07-30T18:00:00Z",
          janelaSegundos: 14400,
          titulo: "Reajuste do contrato Acme",
          quemEspera: "Comercial",
          oQueTrava: "cliente aguarda retorno",
          valorEmJogo: 90000,
        },
      ]),
    propor: h.propor,
    concluir: h.concluir,
    devolver: h.devolver,
  };
});

import { ExecutorPage } from "../components/ExecutorPage";
import { renderComProviders } from "../test/util";

describe("tela do executor", () => {
  it("lista a delegação com o contrato do silêncio visível", async () => {
    renderComProviders(<ExecutorPage />);
    await screen.findByText(/Proposta: reajuste de 4,2%/);
    // contrato do silêncio (executa por ausência) explicado
    expect(screen.getByText(/executa por ausência 4h após o prazo/i)).toBeInTheDocument();
  });

  it("concluir dispara a mutação", async () => {
    renderComProviders(<ExecutorPage />);
    await screen.findByText(/reajuste/);
    fireEvent.click(screen.getByRole("button", { name: "Concluir" }));
    await waitFor(() =>
      expect(h.concluir).toHaveBeenCalledWith("dddddddd-dddd-dddd-dddd-dddddddddddd", "concluído"),
    );
  });

  it("devolver exige motivo e dispara a mutação", async () => {
    renderComProviders(<ExecutorPage />);
    await screen.findByText(/reajuste/);
    expect(screen.getByRole("button", { name: "Devolver" })).toBeDisabled(); // sem motivo, não devolve

    fireEvent.change(screen.getByLabelText("motivo"), { target: { value: "não é comigo" } });
    fireEvent.click(screen.getByRole("button", { name: "Devolver" }));
    await waitFor(() =>
      expect(h.devolver).toHaveBeenCalledWith("dddddddd-dddd-dddd-dddd-dddddddddddd", "não é comigo"),
    );
  });
});
