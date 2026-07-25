import { Button, Paper, Stack, Text, TextInput, Title } from "@mantine/core";
import { useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { definirSessao, limparId, RE_UUID } from "../api/config";

/**
 * Tela de sessão do PILOTO (profile demo, sem OIDC). O gestor/executor informa
 * a organização e a própria pessoa — ou entra por um link pronto
 * (?org=...&pessoa=...&rotulo=...). Em produção isto some: o contexto vem do
 * token OIDC. A API valida o tenant de qualquer forma (INV-15).
 */
export function SessaoPage() {
  const navigate = useNavigate();
  const [org, setOrg] = useState("");
  const [pessoa, setPessoa] = useState("");
  const [rotulo, setRotulo] = useState("");
  const [erro, setErro] = useState<string | null>(null);

  // Link compartilhável: ?org=&pessoa=&rotulo= — se vier completo, entra direto.
  useEffect(() => {
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
      <Paper withBorder p="xl" style={{ width: "100%", maxWidth: 460 }}>
        <Title order={3}>Entrar</Title>
        <Text size="sm" c="dimmed" mt={4} mb="md">
          Piloto sem login: informe sua organização e sua pessoa. A fila é sempre
          escopada por organização.
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
          <Button onClick={entrar}>Entrar</Button>
        </Stack>
      </Paper>
    </Stack>
  );
}
