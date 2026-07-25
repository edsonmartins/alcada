import { fireEvent, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const h = vi.hoisted(() => ({ autoavaliar: vi.fn().mockResolvedValue(undefined) }));

vi.mock("../api/esteira", async () => {
  const real = await vi.importActual<typeof import("../api/esteira")>("../api/esteira");
  return {
    ...real,
    getPortalInstancia: () =>
      Promise.resolve({
        esteiraNome: "Homologação de integrador",
        etapaAtualNome: "Validação",
        entrouEm: "2026-07-20T12:00:00Z",
        prazoPrevisto: "2026-07-23T12:00:00Z",
        oQueFalta: [{ chave: "contrato_assinado", descricao: "Contrato assinado" }],
      }),
    autoavaliarInstancia: h.autoavaliar,
  };
});
vi.mock("@tanstack/react-router", () => ({ useParams: () => ({ token: "tok123" }) }));

import { PortalInstanciaPage } from "../components/PortalInstanciaPage";
import { renderComProviders } from "../test/util";

describe("portal público da instância", () => {
  it("mostra estado e o que falta, e envia autoavaliação", async () => {
    renderComProviders(<PortalInstanciaPage />);
    await screen.findByText("Homologação de integrador");
    expect(screen.getByText("Validação")).toBeInTheDocument();
    expect(screen.getByText("Contrato assinado")).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText("Contrato assinado"));
    fireEvent.click(screen.getByRole("button", { name: "Declarar conformidade" }));
    await screen.findByTestId("autoavaliacao-ok");
    await waitFor(() =>
      expect(h.autoavaliar).toHaveBeenCalledWith("tok123", [{ criterioChave: "contrato_assinado", conforme: true }]),
    );
  });
});
