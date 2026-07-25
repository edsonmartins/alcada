import { render, screen } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import { act } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Countdown, fmtRestante } from "../components/Countdown";

function renderCd(ui: React.ReactNode) {
  return render(<MantineProvider>{ui}</MantineProvider>);
}

describe("fmtRestante", () => {
  it("formata acima de uma hora como Xh MMm", () => {
    expect(fmtRestante(2 * 3_600_000 + 5 * 60_000)).toBe("2h 05m");
  });
  it("formata abaixo de uma hora como MM:SS", () => {
    expect(fmtRestante(90_000)).toBe("01:30");
  });
  it("prazo no passado é vencido", () => {
    expect(fmtRestante(0)).toBe("vencido");
    expect(fmtRestante(-1)).toBe("vencido");
  });
});

describe("Countdown", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-07-25T12:00:00Z"));
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("mostra o restante a partir do prazo do servidor", () => {
    // prazo daqui a 2 minutos
    renderCd(<Countdown prazo="2026-07-25T12:02:00Z" />);
    const cd = screen.getByTestId("countdown");
    expect(cd.textContent).toMatch(/faltam 02:00/);
  });

  it("entra em estado urgente abaixo de 10 minutos", () => {
    renderCd(<Countdown prazo="2026-07-25T12:05:00Z" />);
    expect(screen.getByTestId("countdown")).toHaveAttribute("data-urgente", "true");
  });

  it("não é urgente acima de 10 minutos", () => {
    renderCd(<Countdown prazo="2026-07-25T13:00:00Z" />);
    const cd = screen.getByTestId("countdown");
    expect(cd).not.toHaveAttribute("data-urgente");
    expect(cd.textContent).toMatch(/faltam 1h 00m/);
  });

  it("chega a vencido e avisa o pai uma única vez", () => {
    const onVencido = vi.fn();
    renderCd(<Countdown prazo="2026-07-25T12:00:02Z" onVencido={onVencido} />);
    expect(screen.getByTestId("countdown").textContent).toMatch(/faltam 00:02/);

    act(() => {
      vi.advanceTimersByTime(3000); // passa do prazo
    });
    const cd = screen.getByTestId("countdown");
    expect(cd.textContent).toBe("vencido");
    expect(cd).toHaveAttribute("data-vencido", "true");

    act(() => {
      vi.advanceTimersByTime(3000); // continua vencido
    });
    expect(onVencido).toHaveBeenCalledTimes(1);
  });

  it("sem prazo mostra 'sem prazo'", () => {
    renderCd(<Countdown prazo={null} />);
    expect(screen.getByTestId("countdown").textContent).toBe("sem prazo");
  });
});
