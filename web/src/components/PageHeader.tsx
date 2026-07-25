import { Group, Stack, Text, Title } from "@mantine/core";
import type { ReactNode } from "react";

/** Cabeçalho de view (linguagem do protótipo: título grande + subtítulo curto). */
export function PageHeader({ titulo, sub, acoes }: { titulo: string; sub?: string; acoes?: ReactNode }) {
  return (
    <Group justify="space-between" align="flex-end" mb="lg" wrap="nowrap">
      <div>
        <Title order={1}>{titulo}</Title>
        {sub && (
          <Text c="dimmed" size="sm" mt={4}>
            {sub}
          </Text>
        )}
      </div>
      {acoes && <Group gap="xs">{acoes}</Group>}
    </Group>
  );
}

/** Bloco vazio padronizado. */
export function Vazio({ children }: { children: ReactNode }) {
  return (
    <Stack align="center" py={48} gap={4}>
      <Text c="dimmed" size="sm">
        {children}
      </Text>
    </Stack>
  );
}
