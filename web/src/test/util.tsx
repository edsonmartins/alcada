import { MantineProvider } from "@mantine/core";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import type { ReactNode } from "react";
import type { Pendencia } from "../api/types";
import { theme } from "../theme";
import { useUI } from "../store/ui";

export function resetUI() {
  useUI.setState({ cursor: 0, selecao: new Set(), drawerId: null, form: null });
}

export function renderComProviders(ui: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return {
    qc,
    ...render(
      <MantineProvider theme={theme}>
        <QueryClientProvider client={qc}>{ui}</QueryClientProvider>
      </MantineProvider>,
    ),
  };
}

export function pendencia(over: Partial<Pendencia> & { id: string; titulo: string }): Pendencia {
  return {
    classe: "DECISAO",
    horizonte: "SEMANA",
    status: "ENTRADA",
    quemEspera: null,
    temperatura: 0,
    baixaConfianca: false,
    oQueTrava: null,
    valorEmJogo: null,
    prazoImplicito: null,
    criadaEm: null,
    ...over,
  };
}
