import { get, post } from "./client";
import type { CasoEvidencia } from "./regras";
import type { Nivel } from "./types";

export type Resposta = "SIM" | "AGORA_NAO" | "NAO_PERGUNTAR";

export interface PerguntaAprendizado {
  id: string;
  classe: string;
  nivelSugerido: Nivel;
  donoSugerido: string | null;
  ocorrencias: number;
  casos: CasoEvidencia[];
}

export function getPerguntas(): Promise<PerguntaAprendizado[]> {
  return get<PerguntaAprendizado[]>("/v1/aprendizado/perguntas");
}

export function responder(id: string, resposta: Resposta): Promise<void> {
  return post<void>(`/v1/aprendizado/perguntas/${id}/responder`, { resposta });
}
