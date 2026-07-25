import { get, post } from "./client";
import type { AdiarBody, ItemHoje, Pendencia, RepassarBody, SaidaDireta } from "./types";

export function getEntrada(): Promise<Pendencia[]> {
  return get<Pendencia[]>("/v1/pendencias?status=ENTRADA");
}

export function getHoje(): Promise<ItemHoje[]> {
  return get<ItemHoje[]>("/v1/hoje");
}

/** Saídas diretas — sem formulário (a janela de desfazer é a segurança). */
export function aplicarSaida(id: string, saida: SaidaDireta): Promise<void> {
  switch (saida) {
    case "resolver":
      return post<void>(`/v1/pendencias/${id}/resolver`, {});
    case "reservar":
      return post<void>(`/v1/pendencias/${id}/reservar`, {
        agendadoPara: proximoDiaUtil(),
      });
    case "repousar":
      return post<void>(`/v1/pendencias/${id}/repousar`, { voltaEm: emDias(7) });
  }
}

export function repassar(id: string, body: RepassarBody): Promise<void> {
  return post<void>(`/v1/pendencias/${id}/repassar`, body);
}

/** @returns a oferta diferenciada (bloco_decisao | repassar | cobrar_insumo). */
export function adiar(id: string, body: AdiarBody): Promise<{ oferta: string }> {
  return post<{ oferta: string }>(`/v1/pendencias/${id}/adiar`, body);
}

function emDias(dias: number): string {
  const d = new Date();
  d.setDate(d.getDate() + dias);
  return d.toISOString();
}

function proximoDiaUtil(): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  d.setHours(9, 0, 0, 0);
  return d.toISOString();
}
