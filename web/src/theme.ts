import { createTheme, Paper } from "@mantine/core";

/**
 * Sistema visual do protótipo (spec/prototipo/alcada-sistema.html):
 * corpo em Instrument Sans, títulos em Bricolage Grotesque, rótulos/contadores em
 * IBM Plex Mono. Papel #EEF0F4 (via global.css), cards brancos, acento azul
 * (repassar #2B57D4 ≈ indigo). Sidebar navy (cor "tinta") fica no Layout.
 */
export const theme = createTheme({
  primaryColor: "indigo",
  primaryShade: 7,
  fontFamily: "'Instrument Sans', system-ui, sans-serif",
  fontFamilyMonospace: "'IBM Plex Mono', ui-monospace, monospace",
  headings: {
    fontFamily: "'Bricolage Grotesque', 'Instrument Sans', sans-serif",
    fontWeight: "700",
    sizes: {
      h1: { fontWeight: "800", fontSize: "1.7rem" },
      h4: { fontWeight: "800", fontSize: "1.35rem" },
      h5: { fontWeight: "700", fontSize: "1.05rem" },
      h6: { fontWeight: "700", fontSize: "0.9rem" },
    },
  },
  defaultRadius: "md",
  cursorType: "pointer",
  components: {
    // Cards com borda + leve profundidade por padrão (linguagem do protótipo).
    Paper: Paper.extend({ defaultProps: { withBorder: true, shadow: "xs", radius: "md" } }),
  },
  colors: {
    // navy da sidebar/superfícies escuras (índice 9 ≈ #131A2B)
    tinta: [
      "#eceef3", "#d5dae6", "#aab4cc", "#7d8cb0", "#5a6c98",
      "#43578a", "#33456f", "#26314c", "#1b2439", "#131a2b",
    ],
  },
});
