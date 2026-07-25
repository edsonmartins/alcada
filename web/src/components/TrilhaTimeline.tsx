import { Text, Timeline } from "@mantine/core";
import { useQuery } from "@tanstack/react-query";
import { getTrilha, type EventoTrilha, type TipoEventoTrilha } from "../api/trilha";

/** Rótulos legíveis por tipo de evento (vocabulário fechado do backend). */
const ROTULOS: Record<string, string> = {
  CAPTADA: "Captada",
  CLASSIFICADA: "Classificada",
  FUNDIDA: "Fundida a um item existente",
  DESFUNDIDA: "Separada de outro item",
  PRIORIZADA: "Priorizada para hoje",
  RESOLVIDA: "Resolvida",
  RESERVADA: "Reservada em bloco",
  REPOUSADA: "Repousada",
  ADIADA: "Adiada",
  REPASSADA: "Repassada",
  DELEGADA: "Delegada",
  PROPOSTA_REGISTRADA: "Proposta registrada",
  JANELA_INICIADA: "Janela de silêncio iniciada",
  EXECUTADA: "Executada",
  EXECUTADA_POR_AUSENCIA: "Executado por ausência",
  DESFEITA_NA_JANELA: "Desfeita dentro da janela",
  INTERROMPIDA: "Interrompida pelo gestor",
  ESCALADA: "Escalada ao gestor",
  CONVERTIDA_POR_AUSENCIA: "Convertida por ausência",
  NIVEL_PROMOVIDO: "Nível promovido",
  DEVOLVIDA_PELO_EXECUTOR: "Devolvida pelo executor",
};

function rotulo(tipo: TipoEventoTrilha): string {
  return ROTULOS[tipo] ?? tipo;
}

const FMT_SP = new Intl.DateTimeFormat("pt-BR", {
  timeZone: "America/Sao_Paulo",
  day: "2-digit",
  month: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
});

/** Horário do banco (UTC) apresentado em America/Sao_Paulo (regra do CLAUDE.md §4). */
function quando(iso: string): string {
  const t = Date.parse(iso);
  return Number.isNaN(t) ? iso : FMT_SP.format(t);
}

/** Quem agiu, em forma curta: HUMANO:{id} → "gestor/executor"; SISTEMA:{regra} → "sistema". */
function ator(a: string): string {
  if (a.startsWith("HUMANO:")) return "pessoa";
  if (a.startsWith("SISTEMA:")) return "sistema";
  if (a.startsWith("ASSISTENTE:")) return "assistente";
  return a;
}

/**
 * Linha do tempo da trilha de uma pendência (append-only, INV-11). O evento de
 * execução por ausência é destacado — é o coração da demonstração G2: o gestor
 * aceita o N2 pelo silêncio, e a trilha registra sozinha.
 * Referência visual: spec/prototipo/alcada-sistema.html (.hist).
 */
export function TrilhaTimeline({
  pendenciaId,
  enabled = true,
}: {
  pendenciaId: string;
  /** Busca preguiçosa: só consulta quando a trilha está visível. */
  enabled?: boolean;
}) {
  const { data, isLoading } = useQuery({
    queryKey: ["trilha", pendenciaId],
    queryFn: () => getTrilha(pendenciaId),
    refetchInterval: 5000,
    enabled,
  });

  if (isLoading) {
    return (
      <Text size="xs" c="dimmed">
        carregando trilha…
      </Text>
    );
  }
  const eventos = data ?? [];
  if (eventos.length === 0) {
    return (
      <Text size="xs" c="dimmed">
        Captado automaticamente, ainda sem ação.
      </Text>
    );
  }

  // Mais recente no topo (o backend devolve em ordem de ocorrência).
  const ordenados = [...eventos].reverse();
  const ativo = ordenados.findIndex((e) => e.tipo === "EXECUTADA_POR_AUSENCIA");

  return (
    <Timeline active={ativo} bulletSize={14} lineWidth={2} data-testid="trilha">
      {ordenados.map((e) => (
        <Timeline.Item
          key={e.id}
          data-testid={`evento-${e.tipo}`}
          title={
            <Text size="sm" fw={e.tipo === "EXECUTADA_POR_AUSENCIA" ? 700 : 500}>
              {rotulo(e.tipo)}
            </Text>
          }
        >
          <Text size="xs" c="dimmed">
            {quando(e.ocorridoEm)} · {ator(e.ator)}
          </Text>
        </Timeline.Item>
      ))}
    </Timeline>
  );
}

export { ROTULOS };
export type { EventoTrilha };
