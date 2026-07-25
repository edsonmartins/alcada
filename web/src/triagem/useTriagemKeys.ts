import { useEffect, useRef } from "react";
import type { Pendencia, SaidaDireta } from "../api/types";
import { useUI } from "../store/ui";

interface Args {
  itens: Pendencia[];
  aplicar: (id: string, saida: SaidaDireta) => void;
  aplicarLote: (ids: string[], saida: SaidaDireta) => void;
}

/**
 * Triagem inteira pelo teclado (protótipo): `j`/`k` navegam, `Enter` abre,
 * `1–4` decidem, `a` adia (secundário), `Espaço` seleciona para o lote, `Esc`
 * fecha. Nunca há arrastar (ADR-0018).
 */
export function useTriagemKeys({ itens, aplicar, aplicarLote }: Args) {
  const itensRef = useRef(itens);
  itensRef.current = itens;

  useEffect(() => {
    function emCampo() {
      const el = document.activeElement;
      return !!el && /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName);
    }

    function onKey(e: KeyboardEvent) {
      if (emCampo()) return;
      const ui = useUI.getState();
      const lista = itensRef.current;
      const total = lista.length;
      const atual = lista[ui.cursor];

      switch (e.key) {
        case "j":
        case "ArrowDown":
          e.preventDefault();
          ui.moverCursor(1, total);
          return;
        case "k":
        case "ArrowUp":
          e.preventDefault();
          ui.moverCursor(-1, total);
          return;
        case "Enter":
          if (atual) {
            e.preventDefault();
            ui.abrirDrawer(atual.id);
          }
          return;
        case " ":
          if (atual) {
            e.preventDefault();
            ui.alternarSelecao(atual.id);
          }
          return;
        case "Escape":
          if (ui.drawerId) ui.fecharDrawer();
          else ui.limparSelecao();
          return;
      }

      if (!atual) return;
      const sel = [...ui.selecao];

      switch (e.key) {
        case "1": // Resolver — em lote se houver seleção
          e.preventDefault();
          if (sel.length) {
            aplicarLote(sel, "resolver");
            ui.limparSelecao();
          } else aplicar(atual.id, "resolver");
          return;
        case "2": // Repassar — formulário (dono/nível/prazo)
          e.preventDefault();
          ui.abrirDrawer(atual.id, "repassar");
          return;
        case "3": // Reservar
          e.preventDefault();
          aplicar(atual.id, "reservar");
          return;
        case "4": // Repousar — em lote se houver seleção
          e.preventDefault();
          if (sel.length) {
            aplicarLote(sel, "repousar");
            ui.limparSelecao();
          } else aplicar(atual.id, "repousar");
          return;
        case "a": // Adiar — ação secundária, com formulário (ADR-0002)
          e.preventDefault();
          ui.abrirDrawer(atual.id, "adiar");
          return;
      }
    }

    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [aplicar, aplicarLote]);
}
