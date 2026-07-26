import { get } from "./client";

export interface ItemAdiado {
  id: string;
  titulo: string;
  adiadoCount: number;
  oQueTrava: string | null;
  quemEspera: string | null;
  valorEmJogo: number | null;
}

export interface RadarDados {
  dependeDoGestor: { qtd: number; total: number; pct: number };
  rodandoSemVoce: number;
  adiados: ItemAdiado[];
  piorEspera: { pendenciaId: string; titulo: string; dias: number; quemEspera: string | null } | null;
  autonomia: { deliberada: number; porAusencia: number; devolvida: number; escalada: number; promovida: number };
  fechamentoCanal: { entregue: number; falho: number; impossivel: number };
  encolhimento: Array<{ semana: string; entraram: number; fecharam: number }>;
  saudeGateway: { chamadas: number; falhas: number; custo: number };
}

export interface RevisaoDados {
  entrada: { qtd: number; itens: Array<{ id: string; titulo: string; quemEspera: string | null }> };
  adiados: ItemAdiado[];
  podeVirarRegra: Array<{ classe: string; ocorrencias: number }>;
  resumoSemana: {
    resolvidas: number;
    executadas: number;
    delegadas: number;
    escaladas: number;
    devolvidas: number;
    fechadas: number;
  };
  conducao: { entrada: string; adiados: string; regras: string; resumo: string };
}

export function getRadar(): Promise<RadarDados> {
  return get<RadarDados>("/v1/radar");
}

export function getRevisao(): Promise<RevisaoDados> {
  return get<RevisaoDados>("/v1/revisao-semanal");
}
