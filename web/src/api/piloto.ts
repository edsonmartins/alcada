import { getAdmin, postAdmin } from "./client";

export interface RelatorioPiloto {
  inicio: string;
  fim: string;
  n2: { propostas: number; porAusencia: number; intervencoes: number; devolucoes: number; escaladas: number; reversoes: number };
  captura: { capturados: number; escapes: number; escapePct: number; amostra: number; falsosNegativos: number; inconclusivos: number; decisoesForaDaFila: number; aviso: string };
  autonomia: { fechados: number; autonomos: number; fracaoPct: number };
  fontes: Array<{ id: string; nome: string; ativa: boolean; ultimoEvento: string | null; vistas: number; processadas: number; acao: string }>;
}

export interface DescartePiloto { id: string; motivo: string; ocorridoEm: string; fonte: string; trecho: string }

export function getRelatorio(inicio: string, fim: string): Promise<RelatorioPiloto> {
  return getAdmin(`/v1/piloto/relatorio?inicio=${encodeURIComponent(inicio)}&fim=${encodeURIComponent(fim)}`);
}
export function getAmostra(inicio: string, fim: string): Promise<DescartePiloto[]> {
  return getAdmin(`/v1/piloto/descartes/amostra?inicio=${encodeURIComponent(inicio)}&fim=${encodeURIComponent(fim)}&limite=20&semente=piloto`);
}
export function avaliarDescarte(id: string, resultado: "ERA_PENDENCIA" | "NAO_ERA" | "INCONCLUSIVO") {
  return postAdmin<{ id: string }>(`/v1/piloto/descartes/${id}/avaliacoes`, { resultado });
}
export function reconciliar(semana: string, decisoesForaDaFila: number, observacao?: string) {
  return postAdmin<{ id: string }>("/v1/piloto/reconciliacoes", { semana, decisoesForaDaFila, observacao });
}
