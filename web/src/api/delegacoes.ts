import { get, post } from "./client";
import type { Nivel } from "./types";

export interface Delegacao {
  id: string;
  pendenciaId: string;
  donoId: string;
  nivel: Nivel;
  status: "ABERTA" | "PROPOSTA" | "AGUARDANDO_JANELA" | "EXECUTADA" | "DEVOLVIDA" | "ESCALADA";
  proposta: string | null;
  prazo: string | null;
  janelaSegundos: number;
  titulo: string | null;
  quemEspera: string | null;
  oQueTrava: string | null;
  valorEmJogo: number | null;
}

/** Fronteira de autorização: retorna só as delegações do executor autenticado. */
export function getMinhasDelegacoes(): Promise<Delegacao[]> {
  return get<Delegacao[]>("/v1/delegacoes");
}

export function propor(id: string, proposta: string): Promise<void> {
  return post<void>(`/v1/delegacoes/${id}/propor`, { proposta });
}

export function concluir(id: string, resultado: string): Promise<void> {
  return post<void>(`/v1/delegacoes/${id}/concluir`, { resultado });
}

export function devolver(id: string, motivo: string): Promise<void> {
  return post<void>(`/v1/delegacoes/${id}/devolver`, { motivo });
}

/** Explicação do contrato do silêncio, para o executor entender o que acontece se não agir. */
export function contratoDoSilencio(d: Delegacao): string {
  const horas = Math.round(d.janelaSegundos / 3600);
  if (d.status === "PROPOSTA" || d.status === "AGUARDANDO_JANELA") {
    return `Você propôs. Se o gestor não intervir, executa por ausência ${horas}h após o prazo.`;
  }
  return "Sem proposta registrada: no silêncio de ambos, o item escala ao gestor — não executa em branco.";
}
