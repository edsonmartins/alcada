import { create } from "zustand";

export type FormDrawer = "repassar" | "adiar" | "pedido_informacao" | null;

interface UIState {
  cursor: number;
  selecao: Set<string>;
  drawerId: string | null;
  form: FormDrawer;
  moverCursor: (delta: number, total: number) => void;
  setCursor: (i: number) => void;
  alternarSelecao: (id: string) => void;
  limparSelecao: () => void;
  abrirDrawer: (id: string, form?: FormDrawer) => void;
  fecharDrawer: () => void;
}

export const useUI = create<UIState>((set) => ({
  cursor: 0,
  selecao: new Set(),
  drawerId: null,
  form: null,
  moverCursor: (delta, total) =>
    set((s) => ({ cursor: total === 0 ? 0 : Math.max(0, Math.min(total - 1, s.cursor + delta)) })),
  setCursor: (i) => set({ cursor: i }),
  alternarSelecao: (id) =>
    set((s) => {
      const sel = new Set(s.selecao);
      if (sel.has(id)) sel.delete(id);
      else sel.add(id);
      return { selecao: sel };
    }),
  limparSelecao: () => set({ selecao: new Set() }),
  abrirDrawer: (id, form = null) => set({ drawerId: id, form }),
  fecharDrawer: () => set({ drawerId: null, form: null }),
}));
