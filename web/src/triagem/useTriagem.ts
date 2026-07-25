import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { aplicarSaida, getEntrada } from "../api/pendencias";
import type { Pendencia, SaidaDireta } from "../api/types";

/** Janela de desfazer (INV-14): antes disso, nenhum efeito externo é enviado. */
export const JANELA_MS = 5000;

export const ENTRADA_KEY = ["entrada"] as const;

export interface Pendente {
  token: string;
  ids: string[];
  saida: SaidaDireta;
  snapshot: Pendencia[];
}

/**
 * Triagem com mutação otimista: a saída some da lista na hora, mas o efeito só é
 * enviado ao servidor quando a janela de desfazer fecha. Desfazer cancela o
 * envio — nada saiu para fora.
 */
export function useTriagem() {
  const qc = useQueryClient();
  const query = useQuery({ queryKey: ENTRADA_KEY, queryFn: getEntrada });
  const [pendentes, setPendentes] = useState<Pendente[]>([]);
  const timers = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

  const lista = (): Pendencia[] => qc.getQueryData<Pendencia[]>(ENTRADA_KEY) ?? [];
  const setLista = (l: Pendencia[]) => qc.setQueryData(ENTRADA_KEY, l);

  function aplicarLote(ids: string[], saida: SaidaDireta) {
    if (ids.length === 0) return;
    const token = crypto.randomUUID();
    const snapshot = lista();
    setLista(snapshot.filter((i) => !ids.includes(i.id)));

    const t = setTimeout(() => {
      timers.current.delete(token);
      setPendentes((p) => p.filter((x) => x.token !== token));
      void Promise.all(ids.map((id) => aplicarSaida(id, saida))).then(() =>
        qc.invalidateQueries({ queryKey: ENTRADA_KEY }),
      );
    }, JANELA_MS);

    timers.current.set(token, t);
    setPendentes((p) => [...p, { token, ids, saida, snapshot }]);
  }

  function aplicar(id: string, saida: SaidaDireta) {
    aplicarLote([id], saida);
  }

  function desfazer(token: string) {
    const t = timers.current.get(token);
    if (t) clearTimeout(t);
    timers.current.delete(token);
    setPendentes((p) => {
      const alvo = p.find((x) => x.token === token);
      if (alvo) setLista(alvo.snapshot);
      return p.filter((x) => x.token !== token);
    });
  }

  return {
    itens: query.data ?? [],
    carregando: query.isLoading,
    pendentes,
    aplicar,
    aplicarLote,
    desfazer,
  };
}
