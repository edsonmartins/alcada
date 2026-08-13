import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("../api/piloto", () => ({
  getRelatorio: () => Promise.resolve({ inicio:"2026-08-01",fim:"2026-08-14",n2:{propostas:4,porAusencia:2,intervencoes:1,devolucoes:0,escaladas:1,reversoes:0},captura:{capturados:10,escapes:1,escapePct:9.1,amostra:3,falsosNegativos:1,inconclusivos:0,decisoesForaDaFila:2,aviso:"A taxa de escape é piso; não é recall exato."},autonomia:{fechados:8,autonomos:2,fracaoPct:25},fontes:[{id:"f1",nome:"WhatsApp",ativa:true,ultimoEvento:null,vistas:3,processadas:1,acao:"TESTAR_FONTE"}] }),
  getAmostra: () => Promise.resolve([]), avaliarDescarte: vi.fn(), reconciliar: vi.fn(),
}));
import { PilotoPage } from "../components/PilotoPage";
import { renderComProviders } from "../test/util";

describe("instrumentação do piloto", () => {
  it("expõe evidência sem declarar o gate aprovado", async () => {
    renderComProviders(<PilotoPage />);
    await screen.findByText("25%");
    expect(screen.getByText(/não é recall exato/i)).toBeInTheDocument();
    expect(screen.getByText(/não decide os gates sozinho/i)).toBeInTheDocument();
    expect(screen.getByText("testar fonte")).toBeInTheDocument();
  });
});
