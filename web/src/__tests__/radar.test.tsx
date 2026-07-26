import { fireEvent, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { RadarDados, RevisaoDados } from "../api/metricas";

const RADAR: RadarDados = {
  dependeDoGestor: { qtd: 3, total: 4, pct: 75 },
  rodandoSemVoce: 2,
  adiados: [
    { id: "p1", titulo: "Contrato Panorama", adiadoCount: 4, oQueTrava: "integrador parado", quemEspera: "Comercial", valorEmJogo: 240000 },
  ],
  piorEspera: { pendenciaId: "p1", titulo: "Contrato Panorama", dias: 21, quemEspera: "Comercial" },
  autonomia: { deliberada: 5, porAusencia: 8, devolvida: 1, escalada: 2, promovida: 3 },
  fechamentoCanal: { entregue: 10, falho: 1, impossivel: 2 },
  encolhimento: [
    { semana: "2026-06-01", entraram: 30, fecharam: 20 },
    { semana: "2026-06-08", entraram: 12, fecharam: 18 },
  ],
};

const REVISAO: RevisaoDados = {
  entrada: { qtd: 2, itens: [{ id: "e1", titulo: "Aprovar reembolso", quemEspera: "RH" }] },
  adiados: [],
  podeVirarRegra: [{ classe: "DECISAO", ocorrencias: 4 }],
  resumoSemana: { resolvidas: 6, executadas: 3, delegadas: 4, escaladas: 1, devolvidas: 0, fechadas: 9 },
  conducao: {
    entrada: "2 itens na entrada. Esvazie: decida, delegue ou deixe dormir.",
    adiados: "Nada que você venha adiando 3 vezes ou mais — bom sinal.",
    regras: "DECISAO se repetiu 4 vezes — candidata a virar autonomia.",
    resumo: "Na semana: 6 resolvidas, 3 executadas, 4 delegadas.",
  },
};

vi.mock("../api/metricas", async () => {
  const real = await vi.importActual<typeof import("../api/metricas")>("../api/metricas");
  return { ...real, getRadar: () => Promise.resolve(RADAR), getRevisao: () => Promise.resolve(REVISAO) };
});
vi.mock("../api/pendencias", () => ({ aplicarSaida: vi.fn().mockResolvedValue(undefined) }));

import { RadarPage } from "../components/RadarPage";
import { SextaPage } from "../components/SextaPage";
import { renderComProviders } from "../test/util";

describe("radar", () => {
  it("mostra dependência, contagem honesta e adiados com ação", async () => {
    renderComProviders(<RadarPage />);
    await screen.findByText("75%");
    // contagem honesta: ausência e deliberada são números distintos (ADR-0024)
    expect(screen.getByText("Executadas por ausência")).toBeInTheDocument();
    expect(screen.getByText("Executadas por você")).toBeInTheDocument();
    // adiado com ação
    expect(screen.getByText("Contrato Panorama")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Resolver" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Soltar" })).toBeInTheDocument();
  });
});

describe("revisão de sexta", () => {
  it("percorre os passos até o resumo", async () => {
    renderComProviders(<SextaPage />);
    await screen.findByText("1. A fila de entrada");
    fireEvent.click(screen.getByRole("button", { name: "Próximo" })); // adiados
    fireEvent.click(screen.getByRole("button", { name: "Próximo" })); // dica
    expect(screen.getByText(/pode virar regra/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Próximo" })); // resumo
    expect(screen.getByText("4. Resumo da semana")).toBeInTheDocument();
    expect(screen.getByText("resolvidas")).toBeInTheDocument();
  });
});
