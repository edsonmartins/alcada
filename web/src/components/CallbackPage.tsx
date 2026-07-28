import { Anchor, Center, Loader, Stack, Text } from "@mantine/core";
import { useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { tratarCallback } from "../api/oidc";

/**
 * Retorno do login do ArchGuard (redirect_uri = /callback). Troca o `code` pelos
 * tokens, aplica a sessão (org_id/pessoa_id das claims) e segue para a Entrada.
 */
export function CallbackPage() {
  const navigate = useNavigate();
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    tratarCallback()
      .then(() => navigate({ to: "/" }))
      .catch((e) => setErro(e instanceof Error ? e.message : String(e)));
  }, [navigate]);

  return (
    <Center h="100vh">
      {erro ? (
        <Stack align="center" gap="xs">
          <Text c="red" ta="center" maw={420}>
            Falha no login: {erro}
          </Text>
          <Anchor href="/entrar" size="sm">
            Voltar para o acesso
          </Anchor>
        </Stack>
      ) : (
        <Stack align="center" gap="sm">
          <Loader />
          <Text c="dimmed">Entrando…</Text>
        </Stack>
      )}
    </Center>
  );
}
