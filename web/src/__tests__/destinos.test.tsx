import { fireEvent, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const h = vi.hoisted(() => ({ repassar: vi.fn().mockResolvedValue(undefined) }));
vi.mock("../api/pendencias", () => ({
  getEntrada: () => Promise.resolve([{ id:"p1",titulo:"Aprovar",classe:"DECISAO",horizonte:"SEMANA",status:"ENTRADA",quemEspera:null,temperatura:0,baixaConfianca:false,oQueTrava:null,valorEmJogo:null,prazoImplicito:null,criadaEm:null,origemGrupo:null,cobrancas:0 }]),
  aplicarSaida: vi.fn(), adiar: vi.fn(), repassar: h.repassar,
}));
vi.mock("../api/destinos", () => ({ getDestinos: () => Promise.resolve([
  {tipo:"INTERNO",id:"11111111-1111-1111-1111-111111111111",nome:"Carolina",detalhe:"Equipe",canal:null,recente:true,usadoNaClasse:false,nivelSugerido:"N2",prazoSugerido:null},
  {tipo:"EXTERNO",id:"22222222-2222-2222-2222-222222222222",nome:"Marcello",detalhe:"EMAIL · m•••@example.com",canal:"EMAIL",recente:false,usadoNaClasse:false,nivelSugerido:null,prazoSugerido:null},
]) }));

import { EntradaPage } from "../components/EntradaPage";
import { renderComProviders, resetUI } from "../test/util";

describe("repasse reconhecível", () => {
  beforeEach(() => { resetUI(); h.repassar.mockClear(); });
  it("seleciona pessoa por nome sem exibir UUID", async () => {
    renderComProviders(<EntradaPage />);
    await screen.findByText("Aprovar");
    fireEvent.keyDown(window,{key:"2"});
    const campo=await screen.findByPlaceholderText("Nome da pessoa ou contato");
    fireEvent.click(campo);
    await screen.findByText(/Carolina — Equipe/);
    expect(screen.queryByPlaceholderText(/id da pessoa/i)).not.toBeInTheDocument();
  });
});
