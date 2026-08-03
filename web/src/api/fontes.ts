import { get, put } from "./client";

/**
 * Fonte de captura do tenant. `linktorChannelId` é o canal de saída (RFC-0008):
 * o aviso de repasse no WhatsApp sai pela primeira fonte WHATSAPP **ativa** com
 * canal configurado.
 */
export interface Fonte {
  id: string;
  tipo: string;
  identificador: string;
  ativa: boolean;
  linktorChannelId: string | null;
}

export function getFontes(): Promise<Fonte[]> {
  return get<Fonte[]>("/v1/fontes");
}

/** Define (ou limpa, com string vazia) o canal do Linktor da fonte. */
export function definirCanal(id: string, linktorChannelId: string): Promise<void> {
  return put<void>(`/v1/fontes/${id}/canal`, { linktorChannelId });
}
