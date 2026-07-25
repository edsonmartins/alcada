import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { EventoTrilha } from "../api/trilha";

const EVENTOS: EventoTrilha[] = [
  {
    id: "e1",
    pendenciaId: "pppppppp-pppp-pppp-pppp-pppppppppppp",
    tipo: "DELEGADA",
    ator: "HUMANO:gggggggg-gggg-gggg-gggg-gggggggggggg",
    ocorridoEm: "2026-07-25T14:00:00Z",
    estadoAnterior: "ENTRADA",
    estadoPosterior: "DELEGADA",
    origem: null,
    carga: null,
  },
  {
    id: "e2",
    pendenciaId: "pppppppp-pppp-pppp-pppp-pppppppppppp",
    tipo: "PROPOSTA_REGISTRADA",
    ator: "HUMANO:eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
    ocorridoEm: "2026-07-25T14:01:00Z",
    estadoAnterior: "DELEGADA",
    estadoPosterior: "AGUARDANDO_JANELA",
    origem: null,
    carga: null,
  },
  {
    id: "e3",
    pendenciaId: "pppppppp-pppp-pppp-pppp-pppppppppppp",
    tipo: "EXECUTADA_POR_AUSENCIA",
    ator: "SISTEMA:motor-autonomia",
    ocorridoEm: "2026-07-25T14:03:00Z",
    estadoAnterior: "AGUARDANDO_JANELA",
    estadoPosterior: "FECHADA",
    origem: null,
    carga: null,
  },
];

vi.mock("../api/trilha", async () => {
  const real = await vi.importActual<typeof import("../api/trilha")>("../api/trilha");
  return { ...real, getTrilha: () => Promise.resolve(EVENTOS) };
});

import { TrilhaTimeline } from "../components/TrilhaTimeline";
import { renderComProviders } from "../test/util";

describe("linha do tempo da trilha", () => {
  it("renderiza os eventos e destaca a execução por ausência", async () => {
    renderComProviders(<TrilhaTimeline pendenciaId="pppppppp-pppp-pppp-pppp-pppppppppppp" />);

    // o evento central da demo G2 aparece com rótulo legível
    await screen.findByText("Executado por ausência");
    expect(screen.getByTestId("evento-EXECUTADA_POR_AUSENCIA")).toBeInTheDocument();
    expect(screen.getByText("Delegada")).toBeInTheDocument();
    expect(screen.getByText("Proposta registrada")).toBeInTheDocument();
  });

  it("apresenta o horário em America/Sao_Paulo (UTC-3)", async () => {
    renderComProviders(<TrilhaTimeline pendenciaId="pppppppp-pppp-pppp-pppp-pppppppppppp" />);
    // 14:03Z → 11:03 em São Paulo
    await screen.findByText(/11:03/);
  });

  it("mostra o ator do sistema na execução por ausência", async () => {
    renderComProviders(<TrilhaTimeline pendenciaId="pppppppp-pppp-pppp-pppp-pppppppppppp" />);
    await screen.findByText("Executado por ausência");
    expect(screen.getAllByText(/sistema/).length).toBeGreaterThan(0);
  });
});
