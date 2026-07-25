import { fireEvent, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { BlocoDados } from "../api/bloco";

const BLOCO: BlocoDados = {
  pendenciaId: "p1",
  titulo: "Aprovar reajuste do contrato",
  classe: "DECISAO",
  dossie: [
    { rotulo: "Quem espera", valor: "Comercial" },
    { rotulo: "Valor em jogo", valor: "R$ 240000" },
  ],
  opcoes: [
    { chave: "aprovar", rotulo: "Aprovar", consequencia: "segue a proposta; o solicitante é avisado" },
    { chave: "recusar", rotulo: "Recusar", consequencia: "nega; o solicitante é avisado" },
  ],
};

const h = vi.hoisted(() => ({
  decidir: vi.fn().mockResolvedValue(undefined),
  redigir: vi.fn().mockResolvedValue({ rascunho: "Comercial, aprovado.", disponivel: false, aviso: "Modelo indisponível" }),
  perguntarDossie: vi.fn().mockResolvedValue({
    encontrou: true, resposta: "Sim, reprovado em 08/07.", fontes: [{ fonteTipo: "MENSAGEM", fonteRef: "m1", trecho: "..." }],
  }),
  navigate: vi.fn(),
}));

vi.mock("../api/bloco", async () => {
  const real = await vi.importActual<typeof import("../api/bloco")>("../api/bloco");
  return { ...real, getBloco: () => Promise.resolve(BLOCO), redigir: h.redigir, decidir: h.decidir, perguntarDossie: h.perguntarDossie };
});
vi.mock("@tanstack/react-router", () => ({
  useParams: () => ({ id: "p1" }),
  useNavigate: () => h.navigate,
}));
vi.mock("./TrilhaTimeline", () => ({ TrilhaTimeline: () => null }));

import { BlocoPage } from "../components/BlocoPage";
import { renderComProviders } from "../test/util";

describe("bloco de decisão", () => {
  it("mostra dossiê e opções, e decide com a opção escolhida", async () => {
    renderComProviders(<BlocoPage />);
    await screen.findByText("Aprovar reajuste do contrato");
    expect(screen.getByText("Quem espera")).toBeInTheDocument();
    expect(screen.getByText(/segue a proposta/)).toBeInTheDocument();

    // escolhe "Aprovar" e gera rascunho (degrada sem modelo)
    fireEvent.click(screen.getByRole("radio", { name: /Aprovar/ }));
    fireEvent.click(screen.getByRole("button", { name: "Gerar rascunho" }));
    await screen.findByTestId("aviso-modelo");

    fireEvent.click(screen.getByRole("button", { name: "Decidir e comunicar" }));
    await waitFor(() => expect(h.decidir).toHaveBeenCalledWith("p1", "Aprovar", "Comercial, aprovado."));
  });

  it("pergunta ao dossiê e mostra resposta com fonte", async () => {
    renderComProviders(<BlocoPage />);
    await screen.findByText("Aprovar reajuste do contrato");
    fireEvent.change(screen.getByPlaceholderText(/já foi reprovado/), { target: { value: "reprovado antes?" } });
    fireEvent.click(screen.getByRole("button", { name: "Perguntar" }));
    await screen.findByTestId("dossie-resposta");
    expect(screen.getByText("Sim, reprovado em 08/07.")).toBeInTheDocument();
    expect(h.perguntarDossie).toHaveBeenCalledWith("p1", "reprovado antes?");
  });
});
