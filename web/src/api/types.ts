export type Classe = "DECISAO" | "BLOQUEIO" | "ESTEIRA";
export type Horizonte = "HOJE" | "SEMANA" | "TRIMESTRE";
export type Status = "ENTRADA" | "DELEGADA" | "AGENDADA" | "DORMINDO" | "FECHADA";
export type Nivel = "N1" | "N2" | "N3";
export type OQueFalta = "NADA" | "INSUMO" | "TERCEIRO";

/** As três saídas aplicáveis direto pelo teclado (repassar e adiar têm formulário). */
export type SaidaDireta = "resolver" | "reservar" | "repousar";

export interface Pendencia {
  id: string;
  titulo: string;
  classe: Classe;
  horizonte: Horizonte;
  status: Status;
  quemEspera: string | null;
  temperatura: number;
  baixaConfianca: boolean;
}

export interface ItemHoje {
  id: string;
  titulo: string;
  justificativa: string;
}

export interface RepassarBody {
  donoId: string;
  nivel: Nivel;
  prazo: string; // ISO-8601
}

export interface AdiarBody {
  voltaEm: string; // ISO-8601
  oQueFalta: OQueFalta;
}

/** Erro RFC 7807 (application/problem+json). */
export class ProblemaError extends Error {
  constructor(
    readonly status: number,
    readonly type: string,
    detail: string,
  ) {
    super(detail);
    this.name = "ProblemaError";
  }
}
