import { get, post } from "./client";

export interface Etapa {
  id: string;
  ordem: number;
  nome: string;
  donoId: string | null;
  etapaDoGestor: boolean;
}
export interface Esteira {
  id: string;
  nome: string;
  etapas: Etapa[];
}
export interface Instancia {
  id: string;
  entidadeExterna: string;
  etapaAtualId: string | null;
  etapaAtualNome: string | null;
  status: string;
  entrouEm: string | null;
}
export interface Criterio {
  chave: string;
  descricao: string;
  tipo: "OBJETIVO" | "JULGAMENTO";
  obrigatorio: boolean;
}
export interface Checklist {
  etapaId: string;
  versao: number;
  criterios: Criterio[];
}
export interface PropostaChecklist {
  objetivos: Array<{ chave: string; descricao: string; fracao: number }>;
  julgamento: string[];
}
export interface AvaliacaoResultado {
  desfecho: string;
  pendenciaId: string | null;
}

export const getEsteiras = () => get<Esteira[]>("/v1/esteiras");
export const getInstancias = (esteiraId: string, etapa?: string) =>
  get<Instancia[]>(`/v1/esteiras/${esteiraId}/instancias${etapa ? `?etapa=${etapa}` : ""}`);
export const criarInstancia = (esteiraId: string, entidadeExterna: string) =>
  post<{ id: string }>(`/v1/esteiras/${esteiraId}/instancias`, { entidadeExterna });
export const avaliar = (
  instanciaId: string,
  resultados: Array<{ criterioChave: string; resultado: string }>,
  apontamentos: Array<{ texto: string; tipo: string }>,
) => post<AvaliacaoResultado>(`/v1/instancias/${instanciaId}/avaliar`, { resultados, apontamentos });
export const avancar = (instanciaId: string) => post<void>(`/v1/instancias/${instanciaId}/avancar`, {});
export const getChecklist = (esteiraId: string) => get<Checklist>(`/v1/esteiras/${esteiraId}/checklist`);
export const getPropostasChecklist = (esteiraId: string) =>
  get<PropostaChecklist>(`/v1/esteiras/${esteiraId}/checklist/propostas`);
export const publicarChecklist = (esteiraId: string, criterios: Criterio[]) =>
  post<{ versao: number }>(`/v1/esteiras/${esteiraId}/checklist`, { criterios });
