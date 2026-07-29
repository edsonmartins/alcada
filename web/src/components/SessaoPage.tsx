import { Button, Divider, Paper, Stack, Text, TextInput, Title } from "@mantine/core";
import { useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { definirSessao, limparId, loginDemo, RE_UUID } from "../api/config";
import { entrarOidc, oidcHabilitado } from "../api/oidc";
import logoVertical from "../assets/logo-vertical.png";

/**
 * Tela de entrada. Caminho padrão e recomendado: login pela página hospedada do
 * ArchGuard (Authorization Code + PKCE) — a senha nunca passa pelo app (INV-1) e
 * o org_id/pessoa_id vêm das claims do token. A entrada manual de identificadores
 * só aparece em modo demo (VITE_LOGIN_DEMO=true), para deploys SEM OIDC. A API
 * valida o tenant de qualquer forma (INV-15).
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
    <Stack align="center" mt="xl">
      <Paper withBorder p={0} style={{ width: "100%", maxWidth: 460, overflow: "hidden" }}>
        <div style={{ background: "#131a2b", padding: "26px 24px", textAlign: "center" }}>
          <img
            src={logoVertical}
            alt="Alçada"
            style={{ height: 150, width: "auto", display: "block", margin: "0 auto" }}
          />
        </div>
        <div style={{ padding: "24px" }}>
          <Title order={3} ta="center">Entrar</Title>
          {oidcHabilitado ? (
            <Stack gap={8} mt="lg">
              <Button onClick={() => void entrarOidc()} size="md">
                Entrar com ArchGuard
              </Button>
              <Text size="xs" c="dimmed" ta="center">
                Login seguro do gestor. Você digita sua senha na página do ArchGuard,
                nunca aqui.
              </Text>
            </Stack>
          ) : (
            !loginDemo && (
              <Text size="sm" c="red" mt="lg" ta="center">
                Login não configurado (OIDC ausente). Fale com o administrador.
              </Text>
            )
          )}

          {loginDemo && (
            <>
              <Divider label="acesso do piloto (sem OIDC)" labelPosition="center" my="md" />
              <Text size="sm" c="dimmed" mb="md">
                Informe sua organização e sua pessoa. A fila é sempre escopada por
                organização.
              </Text>
              <Stack gap="sm">
                <TextInput
                  label="Organização (org_id)"
                  placeholder="00000000-0000-0000-0000-000000000000"
                  value={org}
                  onChange={(e) => setOrg(e.currentTarget.value)}
                  required
                />
                <TextInput
                  label="Pessoa (pessoa_id)"
                  placeholder="00000000-0000-0000-0000-000000000000"
                  value={pessoa}
                  onChange={(e) => setPessoa(e.currentTarget.value)}
                  required
                />
                <TextInput
                  label="Nome (opcional, só para exibir)"
                  placeholder="Ex.: Gestor"
                  value={rotulo}
                  onChange={(e) => setRotulo(e.currentTarget.value)}
                />
                {erro && (
                  <Text size="sm" c="red">
                    {erro}
                  </Text>
                )}
                <Button variant="default" onClick={entrar}>
                  Entrar sem OIDC
                </Button>
              </Stack>
            </>
          )}
        </div>
      </Paper>
    </Stack>
  );
}
