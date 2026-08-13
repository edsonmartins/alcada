import { Anchor, Badge, Group, Paper, Stack, Text, TextInput } from "@mantine/core";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { consultar } from "../api/consulta";
import { formatValor } from "../util/formato";
import { corClasse, rotulo } from "../util/rotulos";

const EXEMPLOS = [
  "quanto está esperando por mim",
  "o que trava por causa do financeiro",
  "o que estou adiando",
];

/**
 * Consulta em linguagem natural sobre a fila (RFC-0004 §3). Pergunta livre →
 * resposta determinística + itens clicáveis (fonte navegável).
 */
export function ConsultaBox() {
  const [pergunta, setPergunta] = useState("");
  const m = useMutation({ mutationFn: consultar });

  const perguntar = (q: string) => {
    const t = q.trim();
    if (t) m.mutate(t);
  };

  const r = m.data;

  return (
    <Paper p="md">
      <Text fw={600} size="sm" mb={6}>
        Perguntar sobre a fila
      </Text>
      <Group gap="xs" wrap="nowrap">
        <TextInput
          aria-label="consulta"
          placeholder="ex.: quanto está esperando por mim"
          value={pergunta}
          onChange={(e) => setPergunta(e.currentTarget.value)}
          onKeyDown={(e) => e.key === "Enter" && perguntar(pergunta)}
          style={{ flex: 1 }}
          data-testid="consulta-input"
        />
      </Group>

      <Group gap={6} mt={8}>
        {EXEMPLOS.map((ex) => (
          <Anchor
            key={ex}
            component="button"
            type="button"
            size="xs"
            c="dimmed"
            onClick={() => {
              setPergunta(ex);
              perguntar(ex);
            }}
          >
            {ex}
          </Anchor>
        ))}
      </Group>

      {m.isPending && (
        <Text size="sm" c="dimmed" mt="sm">
          Consultando…
        </Text>
      )}
      {m.isError && (
        <Text size="sm" c="red" mt="sm">
          Não consegui consultar agora.
        </Text>
      )}

      {r && (
        <Stack gap={6} mt="sm" data-testid="consulta-resposta">
          <Text fw={500}>{r.resposta}</Text>
          {r.itens.length > 0 && (
            <Stack gap={2}>
              {r.itens.map((it) => (
                <Group key={it.id} justify="space-between" wrap="nowrap" gap="xs">
                  <Group gap={8} wrap="nowrap" style={{ minWidth: 0 }}>
                    <Badge size="xs" variant="light" color={corClasse(it.classe)}>
                      {rotulo(it.classe)}
                    </Badge>
                    <Anchor href={it.links.find((l) => l.tipo === "BLOCO")?.href ?? it.links[0]?.href ?? `/itens/${it.id}`} size="sm" lineClamp={1}>
                      {it.titulo}
                    </Anchor>
                  </Group>
                  {formatValor(it.valorEmJogo) && (
                    <Text size="sm" c="dimmed" style={{ flexShrink: 0 }}>
                      {formatValor(it.valorEmJogo)}
                    </Text>
                  )}
                </Group>
              ))}
            </Stack>
          )}
        </Stack>
      )}
    </Paper>
  );
}
