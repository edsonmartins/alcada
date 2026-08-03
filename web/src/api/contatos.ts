import { get, post, put } from "./client";

/** Canal por onde o aviso de repasse chega ao contato (RFC-0008). */
export type CanalContato = "WHATSAPP" | "EMAIL";

/**
 * Contato externo de repasse: quem executa sem ser usuário do Alçada. É dado
 * operacional (escape, INV-02), não uma conta — e o endereço é PII (ADR-0011).
 */
export interface ContatoExterno {
  id: string;
  nome: string;
  canal: CanalContato;
  endereco: string;
}

export type DadosContato = Omit<ContatoExterno, "id">;

export function getContatos(): Promise<ContatoExterno[]> {
  return get<ContatoExterno[]>("/v1/contatos");
}

export function criarContato(body: DadosContato): Promise<{ id: string }> {
  return post<{ id: string }>("/v1/contatos", body);
}

/** Troca nome/canal/endereço — o contato é o mesmo; as delegações seguem válidas. */
export function editarContato(id: string, body: DadosContato): Promise<void> {
  return put<void>(`/v1/contatos/${id}`, body);
}
