import { get, post } from "./client";
import type { Nivel } from "./types";

export interface CasoEvidencia {
  pendenciaId: string;
  titulo: string;
  desfecho: string | null;
  valorEmJogo: number | null;
}

export interface PropostaRegra {
  classe: string;
  ocorrencias: number;
  consistencia: number;
  nivelSugerido: Nivel;
  donoSugerido: string | null;
  casos: CasoEvidencia[];
}

export interface RegraAtiva {
  id: string;
  classe: string;
  nivel: Nivel;
  donoId: string;
  criadaEm: string | null;
}

export function getPropostas(): Promise<PropostaRegra[]> {
  return get<PropostaRegra[]>("/v1/regras/propostas");
}

export function getRegras(): Promise<RegraAtiva[]> {
  return get<RegraAtiva[]>("/v1/regras");
}

export function criarRegra(body: { classe: string; nivel: Nivel; donoId: string }): Promise<{ id: string }> {
  return post<{ id: string }>("/v1/regras", body);
}

export function silenciar(classe: string): Promise<void> {
  return post<void>("/v1/regras/propostas/silenciar", { classe });
}

export function desativar(id: string): Promise<void> {
  return post<void>(`/v1/regras/${id}/desativar`, {});
}
