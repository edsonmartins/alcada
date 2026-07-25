import { get } from "./client";

/** Um tipo de evento da trilha (vocabulário fechado — TipoEvento no backend). */
export type TipoEventoTrilha =
  | "CAPTADA"
  | "CLASSIFICADA"
  | "FUNDIDA"
  | "DESFUNDIDA"
  | "PRIORIZADA"
  | "RESOLVIDA"
  | "RESERVADA"
  | "REPOUSADA"
  | "ADIADA"
  | "REPASSADA"
  | "DELEGADA"
  | "PROPOSTA_REGISTRADA"
  | "JANELA_INICIADA"
  | "EXECUTADA"
  | "EXECUTADA_POR_AUSENCIA"
  | "DESFEITA_NA_JANELA"
  | "INTERROMPIDA"
  | "ESCALADA"
  | "CONVERTIDA_POR_AUSENCIA"
  | "NIVEL_PROMOVIDO"
  | "DEVOLVIDA_PELO_EXECUTOR"
  | (string & {}); // tolera novos tipos sem quebrar o build

/** Evento de trilha já gravado, para leitura (append-only, INV-11). */
export interface EventoTrilha {
  id: string;
  pendenciaId: string;
  tipo: TipoEventoTrilha;
  ator: string; // HUMANO:{id} | SISTEMA:{regra} | ASSISTENTE:{modelo,versão}
  ocorridoEm: string; // ISO-8601 (UTC no banco)
  estadoAnterior: string | null;
  estadoPosterior: string | null;
  origem: string | null;
  carga: string | null;
}

export function getTrilha(pendenciaId: string): Promise<EventoTrilha[]> {
  return get<EventoTrilha[]>(`/v1/pendencias/${pendenciaId}/trilha`);
}
