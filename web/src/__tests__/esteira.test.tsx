import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("../api/esteira", async () => {
  const real = await vi.importActual<typeof import("../api/esteira")>("../api/esteira");
  return {
    ...real,
    getEsteiras: () =>
      Promise.resolve([
        {
          id: "es1",
          nome: "Homologação de integrador",
          etapas: [
            { id: "et1", ordem: 1, nome: "Validação", donoId: null, etapaDoGestor: true },
            { id: "et2", ordem: 2, nome: "Ativação", donoId: null, etapaDoGestor: false },
          ],
        },
      ]),
    getInstancias: () =>
      Promise.resolve([
        { id: "i1", entidadeExterna: "Grupo Panorama", etapaAtualId: "et1", etapaAtualNome: "Validação", status: "EM_ANDAMENTO", entrouEm: null },
      ]),
    getChecklist: () =>
      Promise.resolve({ etapaId: "et1", versao: 1, criterios: [{ chave: "doc_ok", descricao: "Documento em ordem", tipo: "OBJETIVO", obrigatorio: true }] }),
    getPropostasChecklist: () =>
      Promise.resolve({ objetivos: [{ chave: "campos_fiscais", descricao: "campos fiscais", fracao: 0.8 }], julgamento: ["fit cultural"] }),
  };
});

import { EsteiraPage } from "../components/EsteiraPage";
import { renderComProviders } from "../test/util";

describe("esteira", () => {
  it("mostra a esteira, a instância e a proposta de checklist", async () => {
    renderComProviders(<EsteiraPage />);
    await screen.findByText(/Esteira — Homologação de integrador/);
    expect(await screen.findByText("Grupo Panorama")).toBeInTheDocument();
    // mineração §B: objetivo recorrente com ação; julgamento à parte
    expect(await screen.findByText("campos fiscais")).toBeInTheDocument();
    expect(screen.getByText(/80% das reprovações/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Virar critério" })).toBeInTheDocument();
    expect(screen.getByText("fit cultural")).toBeInTheDocument();
  });
});
