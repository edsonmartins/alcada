import { Badge, Button, Group, Paper, Stack, Text, TextInput, Title } from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  concluir,
  contratoDoSilencio,
  devolver,
  getMinhasDelegacoes,
  propor,
  type Delegacao,
} from "../api/delegacoes";

const MINHAS = ["minhas-delegacoes"] as const;

export function ExecutorPage() {
  const { data } = useQuery({ queryKey: MINHAS, queryFn: getMinhasDelegacoes });
  const delegacoes = data ?? [];

  return (
    <Stack>
      <Title order={4}>Delegado a você</Title>
      {delegacoes.map((d) => (
        <CardDelegacao key={d.id} d={d} />
      ))}
      {delegacoes.length === 0 && (
        <Text c="dimmed" ta="center" py="xl">
          Nada delegado a você agora.
        </Text>
      )}
    </Stack>
  );
}

function CardDelegacao({ d }: { d: Delegacao }) {
  const qc = useQueryClient();
  const [proposta, setProposta] = useState(d.proposta ?? "");
  const [resultado, setResultado] = useState("");
  const [motivo, setMotivo] = useState("");

  const invalidar = () => qc.invalidateQueries({ queryKey: MINHAS });
  const mPropor = useMutation({ mutationFn: () => propor(d.id, proposta), onSuccess: invalidar });
  const mConcluir = useMutation({ mutationFn: () => concluir(d.id, resultado || "concluído"), onSuccess: invalidar });
  const mDevolver = useMutation({ mutationFn: () => devolver(d.id, motivo), onSuccess: invalidar });

  return (
    <Paper withBorder p="md" data-testid={`delegacao-${d.id}`}>
      <Group justify="space-between">
        <Text fw={500}>Pendência {d.pendenciaId.slice(0, 8)}…</Text>
        <Group gap="xs">
          <Badge>{d.nivel}</Badge>
          <Badge variant="light">{d.status}</Badge>
        </Group>
      </Group>

      <Text size="sm" mt={4}>
        {d.proposta ? `Proposta: ${d.proposta}` : "Sem proposta registrada."}
      </Text>
      <Text size="xs" c="dimmed">
        Prazo: {d.prazo ?? "—"} · {contratoDoSilencio(d)}
      </Text>

      <Stack gap="xs" mt="sm">
        <Group gap="xs">
          <TextInput
            aria-label="proposta"
            placeholder="sua proposta"
            value={proposta}
            onChange={(e) => setProposta(e.currentTarget.value)}
            style={{ flex: 1 }}
          />
          <Button size="xs" variant="light" onClick={() => mPropor.mutate()}>
            Propor
          </Button>
        </Group>
        <Group gap="xs">
          <TextInput
            aria-label="resultado"
            placeholder="resultado"
            value={resultado}
            onChange={(e) => setResultado(e.currentTarget.value)}
            style={{ flex: 1 }}
          />
          <Button size="xs" onClick={() => mConcluir.mutate()}>
            Concluir
          </Button>
        </Group>
        <Group gap="xs">
          <TextInput
            aria-label="motivo"
            placeholder="motivo da devolução"
            value={motivo}
            onChange={(e) => setMotivo(e.currentTarget.value)}
            style={{ flex: 1 }}
          />
          <Button size="xs" variant="default" onClick={() => mDevolver.mutate()} disabled={!motivo}>
            Devolver
          </Button>
        </Group>
      </Stack>
    </Paper>
  );
}
