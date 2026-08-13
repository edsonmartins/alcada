import { post } from "./client";

export interface ItemConsulta {
  id: string;
  titulo: string;
  classe: string;
  valorEmJogo: number | null;
  status: string;
  links: Array<{ tipo: string; href: string }>;
}

export interface ResultadoConsulta {
  pergunta: string;
  template: string;
  resposta: string;
  itens: ItemConsulta[];
}

/** Consulta em linguagem natural sobre a fila (RFC-0004 §3). */
export function consultar(pergunta: string): Promise<ResultadoConsulta> {
  return post<ResultadoConsulta>("/v1/consulta", { pergunta });
}
