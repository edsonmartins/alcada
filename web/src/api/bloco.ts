import { get, post } from "./client";

export interface BlocoDados {
  pendenciaId: string;
  titulo: string;
  classe: string;
  dossie: Array<{ rotulo: string; valor: string }>;
  opcoes: Array<{ chave: string; rotulo: string; consequencia: string }>;
}
export interface RascunhoResultado {
  rascunho: string;
  disponivel: boolean;
  aviso: string | null;
}

export const getBloco = (id: string) => get<BlocoDados>(`/v1/pendencias/${id}/bloco`);
export const redigir = (id: string, opcao: string, tom: string) =>
  post<RascunhoResultado>(`/v1/pendencias/${id}/bloco/redigir`, { opcao, tom });
export const decidir = (id: string, opcao: string, texto: string) =>
  post<void>(`/v1/pendencias/${id}/decidir`, { opcao, texto });
