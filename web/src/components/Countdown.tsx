import { Badge } from "@mantine/core";
import { useEffect, useRef, useState } from "react";

/** 10 minutos: abaixo disso o prazo entra em estado urgente (igual ao protótipo). */
const URGENTE_MS = 10 * 60 * 1000;

/**
 * Formata o tempo restante como no protótipo (fmtRestante): "Xh MMm" acima de
 * uma hora, "MM:SS" abaixo. Nunca reimplementa a lógica de N2 — só exibe a
 * distância até o prazo que o servidor calculou.
 */
export function fmtRestante(ms: number): string {
  if (ms <= 0) return "vencido";
  const h = Math.floor(ms / 3_600_000);
  const m = Math.floor((ms % 3_600_000) / 60_000);
  const s = Math.floor((ms % 60_000) / 1000);
  if (h > 0) return `${h}h ${String(m).padStart(2, "0")}m`;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

interface CountdownProps {
  /** Prazo ISO-8601 vindo do servidor (deadline da execução por ausência). */
  prazo: string | null;
  /** Chamado uma vez quando o prazo cruza para vencido — o pai revalida os dados. */
  onVencido?: () => void;
}

/**
 * Contagem regressiva até o `prazo` do servidor. É só apresentação: quem
 * executa por ausência é o scheduler no backend. Ao vencer, avisa o pai para
 * refazer o fetch (a trilha então mostra EXECUTADA_POR_AUSENCIA sozinha).
 */
export function Countdown({ prazo, onVencido }: CountdownProps) {
  const alvo = prazo ? Date.parse(prazo) : NaN;
  const [restante, setRestante] = useState(() => (Number.isNaN(alvo) ? NaN : alvo - Date.now()));
  const jaAvisou = useRef(false);

  useEffect(() => {
    if (Number.isNaN(alvo)) return;
    jaAvisou.current = false;
    const tick = () => {
      const r = alvo - Date.now();
      setRestante(r);
      if (r <= 0 && !jaAvisou.current) {
        jaAvisou.current = true;
        onVencido?.();
      }
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [alvo, onVencido]);

  if (Number.isNaN(alvo)) {
    return (
      <Badge variant="light" color="gray" data-testid="countdown">
        sem prazo
      </Badge>
    );
  }

  const vencido = restante <= 0;
  const urgente = !vencido && restante < URGENTE_MS;
  return (
    <Badge
      variant={vencido ? "filled" : "light"}
      color={vencido ? "gray" : urgente ? "red" : "blue"}
      data-testid="countdown"
      data-urgente={urgente || undefined}
      data-vencido={vencido || undefined}
    >
      {vencido ? "vencido" : `faltam ${fmtRestante(restante)}`}
    </Badge>
  );
}
