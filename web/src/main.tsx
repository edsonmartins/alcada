import "@fontsource/instrument-sans/400.css";
import "@fontsource/instrument-sans/500.css";
import "@fontsource/instrument-sans/600.css";
import "@fontsource/bricolage-grotesque/700.css";
import "@fontsource/bricolage-grotesque/800.css";
import "@fontsource/ibm-plex-mono/400.css";
import "@fontsource/ibm-plex-mono/500.css";
import "@mantine/core/styles.css";
import "./global.css";

import { Anchor, MantineProvider } from "@mantine/core";
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
import logoHorizontal from "./assets/logo-horizontal.png";
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

const NAV = [
  {
    grupo: "Trabalho",
    itens: [
      { to: "/", label: "Entrada", ic: "▸" },
      { to: "/hoje", label: "Hoje", ic: "◈" },
      { to: "/executor", label: "Delegado a mim", ic: "◇" },
    ],
  },
  {
    grupo: "Controle",
    itens: [
      { to: "/radar", label: "Radar", ic: "◱" },
      { to: "/alcadas", label: "Alçadas", ic: "▤" },
      { to: "/esteira", label: "Esteira", ic: "▦" },
      { to: "/sexta", label: "Revisão de sexta", ic: "◷" },
    ],
  },
];

function Sidebar() {
  const navigate = useNavigate();
  const quem = rotuloSessao() || (pessoaId() ? pessoaId().slice(0, 8) + "…" : "");
  const iniciais = (quem || "?").trim().slice(0, 2).toUpperCase();
  const sair = () => {
    limparSessao();
    navigate({ to: "/entrar" });
  };
  return (
    <aside style={{ width: 236, background: "#131a2b", color: "#fff", display: "flex", flexDirection: "column", flex: "none" }}>
      <div style={{ padding: "16px 16px 14px", borderBottom: "1px solid #ffffff14" }}>
        <div style={{ background: "#fff", borderRadius: 10, padding: "9px 12px", display: "inline-block" }}>
          <img src={logoHorizontal} alt="Alçada" style={{ height: 26, width: "auto", display: "block" }} />
        </div>
        <div style={{ fontFamily: "'IBM Plex Mono',monospace", fontSize: 9.5, letterSpacing: ".16em", textTransform: "uppercase", color: "#7C89A8", marginTop: 9 }}>
          plano de controle
        </div>
      </div>
      <nav style={{ padding: "6px 8px", flex: 1, overflowY: "auto" }}>
        {NAV.map((g) => (
          <div key={g.grupo}>
            <div className="sb-grp">{g.grupo}</div>
            {g.itens.map((i) => (
              <Link key={i.to} to={i.to} className="sb-nav-item"
                activeProps={{ className: "sb-nav-item ativo" }} activeOptions={{ exact: i.to === "/" }}>
                <span className="ic">{i.ic}</span>
                {i.label}
              </Link>
            ))}
          </div>
        ))}
      </nav>
      {temSessao() && (
        <div style={{ padding: "12px 14px", borderTop: "1px solid #ffffff14", display: "flex", alignItems: "center", gap: 9 }}>
          <div style={{ width: 28, height: 28, borderRadius: "50%", background: "#26314c", display: "grid", placeItems: "center", fontSize: 11.5, fontWeight: 600, flex: "none" }}>
            {iniciais}
          </div>
          <div style={{ minWidth: 0, flex: 1 }}>
            <b style={{ fontSize: 12.5, display: "block", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{quem}</b>
            <Anchor component="button" type="button" onClick={sair} style={{ fontSize: 11, color: "#7C89A8" }}>trocar</Anchor>
          </div>
        </div>
      )}
    </aside>
  );
}

function Layout() {
  const navigate = useNavigate();
  const rota = useRouterState({ select: (s) => s.location.pathname });
  const naSessao = rota === "/entrar";
  const portalPublico = rota.startsWith("/portal"); // portal externo: sem login, sem chrome interno

  useEffect(() => {
    if (!naSessao && !portalPublico && !temSessao()) {
      navigate({ to: "/entrar" });
    }
  }, [naSessao, portalPublico, rota, navigate]);

  // Portal público e tela de sessão: sem sidebar (centralizados, sem chrome interno).
  if (portalPublico || naSessao) {
    return <Outlet />;
  }

  return (
    <div style={{ display: "flex", height: "100vh" }}>
      <Sidebar />
      <div style={{ flex: 1, minWidth: 0, overflowY: "auto" }}>
        <main style={{ maxWidth: 1440, padding: "24px 28px 72px" }}>
          <Outlet />
        </main>
      </div>
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
