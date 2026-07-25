import "@mantine/core/styles.css";

import { Anchor, Group, MantineProvider, Text } from "@mantine/core";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  Link,
  Outlet,
  RouterProvider,
  createRootRoute,
  createRoute,
  createRouter,
  useNavigate,
  useRouterState,
} from "@tanstack/react-router";
import { StrictMode, useEffect } from "react";
import { createRoot } from "react-dom/client";
import { limparSessao, pessoaId, rotuloSessao, temSessao } from "./api/config";
import { AlcadasPage } from "./components/AlcadasPage";
import { BlocoPage } from "./components/BlocoPage";
import { EntradaPage } from "./components/EntradaPage";
import { EsteiraPage } from "./components/EsteiraPage";
import { ExecutorPage } from "./components/ExecutorPage";
import { HojePage } from "./components/HojePage";
import { PortalInstanciaPage } from "./components/PortalInstanciaPage";
import { RadarPage } from "./components/RadarPage";
import { SessaoPage } from "./components/SessaoPage";
import { SextaPage } from "./components/SextaPage";
import { theme } from "./theme";

function Layout() {
  const navigate = useNavigate();
  const rota = useRouterState({ select: (s) => s.location.pathname });
  const naSessao = rota === "/entrar";
  const portalPublico = rota.startsWith("/portal"); // portal externo: sem login, sem chrome interno

  // Guarda do piloto: sem sessão, manda para /entrar (menos na /entrar e no portal público).
  useEffect(() => {
    if (!naSessao && !portalPublico && !temSessao()) {
      navigate({ to: "/entrar" });
    }
  }, [naSessao, portalPublico, rota, navigate]);

  if (portalPublico) {
    return <Outlet />;
  }

  const sair = () => {
    limparSessao();
    navigate({ to: "/entrar" });
  };

  const quem = rotuloSessao() || (pessoaId() ? pessoaId().slice(0, 8) + "…" : "");

  return (
    <div style={{ maxWidth: 820, margin: "0 auto", padding: 16 }}>
      <header style={{ display: "flex", alignItems: "center", gap: 16, marginBottom: 16 }}>
        <strong>Alçada</strong>
        {!naSessao && (
          <>
            <Link to="/">Entrada</Link>
            <Link to="/hoje">Hoje</Link>
            <Link to="/executor">Delegado a mim</Link>
            <Link to="/radar">Radar</Link>
            <Link to="/alcadas">Alçadas</Link>
            <Link to="/esteira">Esteira</Link>
            <Link to="/sexta">Sexta</Link>
            {temSessao() && (
              <Group gap={6} ml="auto">
                <Text size="xs" c="dimmed">
                  {quem}
                </Text>
                <Anchor component="button" type="button" size="xs" onClick={sair}>
                  trocar
                </Anchor>
              </Group>
            )}
          </>
        )}
      </header>
      <Outlet />
      {!naSessao && (
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
      )}
    </div>
  );
}

const rootRoute = createRootRoute({ component: Layout });
const indexRoute = createRoute({ getParentRoute: () => rootRoute, path: "/", component: EntradaPage });
const hojeRoute = createRoute({ getParentRoute: () => rootRoute, path: "/hoje", component: HojePage });
const executorRoute = createRoute({ getParentRoute: () => rootRoute, path: "/executor", component: ExecutorPage });
const radarRoute = createRoute({ getParentRoute: () => rootRoute, path: "/radar", component: RadarPage });
const alcadasRoute = createRoute({ getParentRoute: () => rootRoute, path: "/alcadas", component: AlcadasPage });
const esteiraRoute = createRoute({ getParentRoute: () => rootRoute, path: "/esteira", component: EsteiraPage });
const blocoRoute = createRoute({ getParentRoute: () => rootRoute, path: "/bloco/$id", component: BlocoPage });
const portalInstanciaRoute = createRoute({ getParentRoute: () => rootRoute, path: "/portal/instancia/$token", component: PortalInstanciaPage });
const sextaRoute = createRoute({ getParentRoute: () => rootRoute, path: "/sexta", component: SextaPage });
const sessaoRoute = createRoute({ getParentRoute: () => rootRoute, path: "/entrar", component: SessaoPage });
const router = createRouter({
  routeTree: rootRoute.addChildren([
    indexRoute, hojeRoute, executorRoute, radarRoute, alcadasRoute, esteiraRoute, blocoRoute,
    portalInstanciaRoute, sextaRoute, sessaoRoute,
  ]),
});

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
