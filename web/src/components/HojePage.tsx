import { Paper, Stack, Text, Title } from "@mantine/core";
import { useQuery } from "@tanstack/react-query";
import { getHoje } from "../api/pendencias";

/** Hoje: no máximo 3 (o backend já limita; a UI reforça). Nada contemplativo. */
export function HojePage() {
  const { data } = useQuery({ queryKey: ["hoje"], queryFn: getHoje });
  const itens = (data ?? []).slice(0, 3);

  return (
    <Stack>
      <Title order={4}>Hoje</Title>
      {itens.map((h) => (
        <Paper key={h.id} withBorder p="sm" data-testid="item-hoje">
          <Text fw={500}>{h.titulo}</Text>
          <Text size="xs" c="dimmed">
            por quê: {h.justificativa}
          </Text>
        </Paper>
      ))}
      {itens.length === 0 && (
        <Text c="dimmed" ta="center" py="xl">
          Nada para hoje.
        </Text>
      )}
    </Stack>
  );
}
