import { Button, Paper, Stack, Text, Title } from "@mantine/core";
import { entrarOidc, oidcHabilitado } from "../api/oidc";
import logoVertical from "../assets/logo-vertical.png";

/**
 * Tela de entrada: login exclusivamente pela página hospedada do ArchGuard
 * (Authorization Code + PKCE). A senha nunca passa pelo app (INV-1); o org_id e o
 * pessoa_id vêm das claims do token. A API valida o tenant de qualquer forma
 * (INV-15). Não há mais entrada manual de identificadores.
 */
export function SessaoPage() {
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
            <Text size="sm" c="red" mt="lg" ta="center">
              Login não configurado (OIDC ausente). Fale com o administrador.
            </Text>
          )}
        </div>
      </Paper>
    </Stack>
  );
}
