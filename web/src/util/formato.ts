const BRL = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
  maximumFractionDigits: 0,
});

/** Valor em jogo abreviado (R$ 42 mil / R$ 1,2 mi). Nulo → null (não exibe). */
export function formatValor(v: number | null | undefined): string | null {
  if (v == null || v === 0) return null;
  if (v >= 1_000_000) return `R$ ${(v / 1_000_000).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} mi`;
  if (v >= 1_000) return `R$ ${Math.round(v / 1000)} mil`;
  return BRL.format(v);
}

/** Há quanto tempo o item espera: "hoje", "há 3 dias", "há 2 sem". */
export function idadeRelativa(iso: string | null | undefined, agora: number = Date.now()): string | null {
  if (!iso) return null;
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return null;
  const dias = Math.floor((agora - t) / 86_400_000);
  if (dias <= 0) return "hoje";
  if (dias === 1) return "há 1 dia";
  if (dias < 14) return `há ${dias} dias`;
  if (dias < 60) return `há ${Math.floor(dias / 7)} sem`;
  return `há ${Math.floor(dias / 30)} meses`;
}

const FMT_DATA = new Intl.DateTimeFormat("pt-BR", {
  timeZone: "America/Sao_Paulo",
  day: "2-digit",
  month: "2-digit",
});

/** Prazo implícito como data curta (25/07) em SP. Nulo → null. */
export function formatPrazo(iso: string | null | undefined): string | null {
  if (!iso) return null;
  const t = Date.parse(iso);
  return Number.isNaN(t) ? null : FMT_DATA.format(t);
}
