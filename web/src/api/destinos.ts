import { get } from "./client";
import type { Classe, Nivel } from "./types";

export interface DestinoRepasseOpcao {
  tipo: "INTERNO" | "EXTERNO";
  id: string;
  nome: string;
  detalhe: string;
  canal: "WHATSAPP" | "EMAIL" | null;
  recente: boolean;
  usadoNaClasse: boolean;
  nivelSugerido: Nivel | null;
  prazoSugerido: string | null;
}

export function getDestinos(busca: string, classe?: Classe): Promise<DestinoRepasseOpcao[]> {
  const params = new URLSearchParams({ busca, limite: "8" });
  if (classe) params.set("classe", classe);
  return get<DestinoRepasseOpcao[]>(`/v1/destinos-repasse?${params}`);
}
