import { Button, Divider, Stack, Text, TextInput, Title } from "@mantine/core";
import { useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { definirSessao, limparId, loginDemo, RE_UUID } from "../api/config";
import { entrarOidc, oidcHabilitado } from "../api/oidc";
import banner from "../assets/banner_alcada_web.png";

const NAVY = "#0B1220";

/**
 * Tela de entrada (web). Login pela página hospedada do ArchGuard (Authorization
 * Code + PKCE) — a senha nunca passa pelo app (INV-1). Visual: banner wide da
 * marca (o "A" ascendente à direita) edge-to-edge, e o conteúdo à esquerda, sobre
 * a área escura livre. A entrada manual só aparece em modo demo (VITE_LOGIN_DEMO).
 */
export function SessaoPage() {
  const navigate = useNavigate();
  const [org, setOrg] = useState("");
  const [pessoa, setPessoa] = useState("");
  const [rotulo, setRotulo] = useState("");
  const [erro, setErro] = useState<string | null>(null);

  // Link compartilhável do piloto (?org=&pessoa=&rotulo=) — só no modo demo.
  useEffect(() => {
    if (!loginDemo) return;
    const q = new URLSearchParams(window.location.search);
    const qOrg = q.get("org") ?? "";
    const qPessoa = q.get("pessoa") ?? "";
    const qRotulo = q.get("rotulo") ?? "";
    if (qOrg) setOrg(qOrg);
    if (qPessoa) setPessoa(qPessoa);
    if (qRotulo) setRotulo(qRotulo);
    if (qOrg.trim() && qPessoa.trim()) {
      definirSessao(qOrg, qPessoa, qRotulo);
      navigate({ to: "/" });
    }
  }, [navigate]);

  const entrar = () => {
    const o = limparId(org);
    const p = limparId(pessoa);
    if (!o || !p) {
      setErro("Informe a organização e a pessoa.");
      return;
    }
    if (!RE_UUID.test(o) || !RE_UUID.test(p)) {
      setErro(
        "org_id e pessoa_id precisam ser UUIDs (36 caracteres, formato 8-4-4-4-12). " +
          "Cole o valor sem aspas — no .env eles ficam entre aspas.",
      );
      return;
    }
    definirSessao(o, p, rotulo);
    navigate({ to: "/" });
  };

  return (
    <div style={{ position: "relative", minHeight: "100dvh", background: NAVY, display: "flex", alignItems: "center" }}>
      {/* Banner wide da marca (o "A" à direita); mantém o A visível ao encolher. */}
      <div
        style={{
          position: "absolute",
          inset: 0,
          backgroundImage: `url(${banner})`,
          backgroundSize: "cover",
          backgroundPosition: "right center",
        }}
      />
      {/* Scrim da esquerda: legibilidade do card sobre a área escura. */}
      <div
        style={{
          position: "absolute",
          inset: 0,
          background:
            "linear-gradient(to right, rgba(11,18,32,0.92) 0%, rgba(11,18,32,0.6) 42%, rgba(11,18,32,0) 74%)",
        }}
      />
      <div style={{ position: "relative", width: "100%", padding: "0 clamp(24px, 8vw, 120px)" }}>
        <div style={{ maxWidth: 400 }}>
          <Title order={1} c="white" style={{ fontSize: 44 }}>Alçada</Title>
          <Text size="md" mt={4} style={{ color: "rgba(255,255,255,0.72)" }}>
            Plano de controle de decisões
          </Text>

          {oidcHabilitado ? (
            <Stack gap={8} mt="xl">
              <Button onClick={() => void entrarOidc()} size="md">
                Entrar com ArchGuard
              </Button>
              <Text size="xs" style={{ color: "rgba(255,255,255,0.6)" }}>
                Login seguro do gestor. Você digita sua senha na página do ArchGuard,
                nunca aqui.
              </Text>
            </Stack>
          ) : (
            !loginDemo && (
              <Text size="sm" c="red.4" mt="xl">
                Login não configurado (OIDC ausente). Fale com o administrador.
              </Text>
            )
          )}

          {loginDemo && (
            <>
              <Divider
                label="acesso do piloto (sem OIDC)"
                labelPosition="center"
                my="md"
                color="rgba(255,255,255,0.2)"
                styles={{ label: { color: "rgba(255,255,255,0.6)" } }}
              />
              <Stack gap="sm">
                <TextInput
                  label="Organização (org_id)"
                  placeholder="00000000-0000-0000-0000-000000000000"
                  value={org}
                  onChange={(e) => setOrg(e.currentTarget.value)}
                  styles={{ label: { color: "white" } }}
                />
                <TextInput
                  label="Pessoa (pessoa_id)"
                  placeholder="00000000-0000-0000-0000-000000000000"
                  value={pessoa}
                  onChange={(e) => setPessoa(e.currentTarget.value)}
                  styles={{ label: { color: "white" } }}
                />
                <TextInput
                  label="Nome (opcional)"
                  placeholder="Ex.: Gestor"
                  value={rotulo}
                  onChange={(e) => setRotulo(e.currentTarget.value)}
                  styles={{ label: { color: "white" } }}
                />
                {erro && <Text size="sm" c="red.4">{erro}</Text>}
                <Button variant="white" color="dark" onClick={entrar}>
                  Entrar sem OIDC
                </Button>
              </Stack>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
