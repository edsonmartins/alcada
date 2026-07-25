import "@mantine/core/styles.css";

import { MantineProvider } from "@mantine/core";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  Link,
  Outlet,
  RouterProvider,
  createRootRoute,
  createRoute,
  createRouter,
} from "@tanstack/react-router";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { EntradaPage } from "./components/EntradaPage";
import { ExecutorPage } from "./components/ExecutorPage";
import { HojePage } from "./components/HojePage";
import { theme } from "./theme";

function Layout() {
  return (
    <div style={{ maxWidth: 820, margin: "0 auto", padding: 16 }}>
      <header style={{ display: "flex", gap: 16, marginBottom: 16 }}>
        <strong>Alçada</strong>
        <Link to="/">Entrada</Link>
        <Link to="/hoje">Hoje</Link>
        <Link to="/executor">Delegado a mim</Link>
      </header>
      <Outlet />
      <div
        style={{
          position: "fixed",
          bottom: 12,
          right: 14,
          fontFamily: "var(--mantine-font-family-monospace)",
          fontSize: 11,
        }}
      >
        <b>j k</b> navegar · <b>1–4</b> decidir · <b>a</b> adiar · <b>espaço</b> lote
      </div>
    </div>
  );
}

const rootRoute = createRootRoute({ component: Layout });
const indexRoute = createRoute({ getParentRoute: () => rootRoute, path: "/", component: EntradaPage });
const hojeRoute = createRoute({ getParentRoute: () => rootRoute, path: "/hoje", component: HojePage });
const executorRoute = createRoute({ getParentRoute: () => rootRoute, path: "/executor", component: ExecutorPage });
const router = createRouter({ routeTree: rootRoute.addChildren([indexRoute, hojeRoute, executorRoute]) });

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <MantineProvider theme={theme}>
      <QueryClientProvider client={qc}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </MantineProvider>
  </StrictMode>,
);
