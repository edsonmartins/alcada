import { Alert, Badge, Button, Checkbox, Group, Paper, Stack, Text, Title } from "@mantine/core";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useParams } from "@tanstack/react-router";
import { useState } from "react";
import { autoavaliarInstancia, getPortalInstancia } from "../api/esteira";

/**
 * Portal público da instância (RFC-0006): sem login, o token na URL é a credencial.
 * Mostra só estado + prazo + o que falta; a contraparte declara conformidade.
 */
export function PortalInstanciaPage() {
  const { token } = useParams({ from: "/portal/instancia/$token" });
  const { data, isError } = useQuery({
    queryKey: ["portal-instancia", token],
    queryFn: () => getPortalInstancia(token),
    retry: false,
  });
  const [marcados, setMarcados] = useState<Record<string, boolean>>({});
  const [enviado, setEnviado] = useState(false);
  const m = useMutation({
    mutationFn: () =>
      autoavaliarInstancia(
        token,
        (data?.oQueFalta ?? []).map((f) => ({ criterioChave: f.chave, conforme: !!marcados[f.chave] })),
      ),
    onSuccess: () => setEnviado(true),
  });

  if (isError) {
    return (
      <div style={{ maxWidth: 620, margin: "40px auto", padding: 16 }}>
        <Alert color="gray">Link inválido ou expirado.</Alert>
      </div>
    );
  }
  if (!data) return null;
  const d = data;
  const fmt = (iso: string | null) =>
    iso ? new Intl.DateTimeFormat("pt-BR", { timeZone: "America/Sao_Paulo", dateStyle: "short" }).format(Date.parse(iso)) : "—";

  return (
    <div style={{ maxWidth: 620, margin: "40px auto", padding: 16 }}>
      <Stack>
        <div>
          <Title order={4}>{d.esteiraNome}</Title>
          <Text size="sm" c="dimmed">Acompanhamento da sua solicitação</Text>
        </div>
        <Paper withBorder p="md">
          <Group justify="space-between"><Text size="sm" c="dimmed">Etapa atual</Text>
            <Badge variant="light">{d.etapaAtualNome ?? "concluída"}</Badge></Group>
          <Group justify="space-between"><Text size="sm" c="dimmed">Entrou em</Text>
            <Text size="sm">{fmt(d.entrouEm)}</Text></Group>
          <Group justify="space-between"><Text size="sm" c="dimmed">Prazo previsto</Text>
            <Text size="sm">{fmt(d.prazoPrevisto)}</Text></Group>
        </Paper>

        <Paper withBorder p="md">
          <Title order={6}>O que falta de você</Title>
          <Text size="xs" c="dimmed" mb="sm">Confirme os itens que já estão prontos.</Text>
          {enviado ? (
            <Alert color="teal" data-testid="autoavaliacao-ok">Recebemos sua declaração. Obrigado.</Alert>
          ) : (
            <Stack gap="xs">
              {d.oQueFalta.map((f) => (
                <Checkbox key={f.chave} label={f.descricao}
                  checked={!!marcados[f.chave]}
                  onChange={(e) => setMarcados((s) => ({ ...s, [f.chave]: e.currentTarget.checked }))} />
              ))}
              {d.oQueFalta.length === 0 && <Text size="sm" c="dimmed">Nada pendente de você agora.</Text>}
              {d.oQueFalta.length > 0 && (
                <Group justify="flex-end">
                  <Button size="xs" onClick={() => m.mutate()}>Declarar conformidade</Button>
                </Group>
              )}
            </Stack>
          )}
        </Paper>
        <Text size="xs" c="dimmed" ta="center">Este link é pessoal. Não compartilhe.</Text>
      </Stack>
    </div>
  );
}
