import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../api/pendencias", () => ({
  getEntrada: () => Promise.resolve([{ id:"p1",titulo:"Contrato",classe:"DECISAO",horizonte:"SEMANA",status:"ENTRADA",quemEspera:null,temperatura:0,baixaConfianca:false }]),
  aplicarSaida: vi.fn(), adiar: vi.fn(), repassar: vi.fn(), pedirInformacao: vi.fn(),
}));
vi.mock("../api/destinos", () => ({ getDestinos: () => Promise.resolve([
  {tipo:"EXTERNO",id:"c1",nome:"Fornecedor",detalhe:"WHATSAPP · final 0099",canal:"WHATSAPP",recente:true,usadoNaClasse:false,nivelSugerido:null,prazoSugerido:null},
]) }));

import { EntradaPage } from "../components/EntradaPage";
import { useUI } from "../store/ui";
import { renderComProviders, resetUI } from "../test/util";

describe("pedido estruturado de informação", () => {
  beforeEach(resetUI);
  it("usa o drawer existente e exige confirmação da pergunta", async () => {
    renderComProviders(<EntradaPage />);
    await screen.findByText("Contrato");
    useUI.getState().abrirDrawer("p1", "pedido_informacao");
    expect(await screen.findByText(/próxima ação/i)).toBeInTheDocument();
    expect(screen.getByLabelText("Pergunta objetiva")).toHaveValue("Pode informar o que falta para: Contrato?");
    expect(screen.getByRole("button", { name: /confirmar e pedir informação/i })).toBeInTheDocument();
  });
});
