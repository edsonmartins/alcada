import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

vi.mock("../api/consulta", () => ({
  consultar: vi.fn(async (pergunta: string) => ({
    pergunta,
    template: "ESPERANDO_MIM",
    resposta: "Há 2 itens esperando por você, somando R$ 3 mil.",
    itens: [{ id: "abc", titulo: "Aprovar reembolso", classe: "DECISAO", valorEmJogo: 2000 }],
  })),
}));

import { ConsultaBox } from "../components/ConsultaBox";
import { renderComProviders } from "../test/util";

describe("ConsultaBox", () => {
  it("mostra a resposta e os itens da consulta", async () => {
    renderComProviders(<ConsultaBox />);
    const input = screen.getByTestId("consulta-input");
    await userEvent.type(input, "quanto está esperando por mim{Enter}");

    const resp = await screen.findByTestId("consulta-resposta");
    expect(resp.textContent).toContain("esperando por você");
    expect(screen.getByText("Aprovar reembolso")).toBeInTheDocument();
  });
});
