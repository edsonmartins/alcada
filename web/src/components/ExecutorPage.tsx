import { Badge, Button, Collapse, Group, Paper, Stack, Text, TextInput, Title } from "@mantine/core";
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
import { formatValor } from "../util/formato";
import { Countdown } from "./Countdown";
import { TrilhaTimeline } from "./TrilhaTimeline";

const MINHAS = ["minhas-delegacoes"] as const;

export function ExecutorPage() {
  // refetch modesto: na janela de silêncio, o servidor executa por ausência e a
  // tela reflete isso sozinha — sem o executor tocar em nada (critério da demo).
  const { data } = useQuery({ queryKey: MINHAS, queryFn: getMinhasDelegacoes, refetchInterval: 5000 });
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
  const [verTrilha, setVerTrilha] = useState(false);

  const invalidar = () => {
    qc.invalidateQueries({ queryKey: MINHAS });
    qc.invalidateQueries({ queryKey: ["trilha", d.pendenciaId] });
  };
  const mPropor = useMutation({ mutationFn: () => propor(d.id, proposta), onSuccess: invalidar });
  const mConcluir = useMutation({ mutationFn: () => concluir(d.id, resultado || "concluído"), onSuccess: invalidar });
  const mDevolver = useMutation({ mutationFn: () => devolver(d.id, motivo), onSuccess: invalidar });

  // Só a delegação em janela de silêncio tem contagem — os demais estados não têm prazo.
  const emJanela = d.status === "PROPOSTA" || d.status === "AGUARDANDO_JANELA";

  return (
    <Paper withBorder p="md" data-testid={`delegacao-${d.id}`}>
      <Group justify="space-between" align="flex-start" wrap="nowrap">
        <div style={{ minWidth: 0 }}>
          <Text fw={500}>{d.titulo ?? `Pendência ${d.pendenciaId.slice(0, 8)}…`}</Text>
          {d.oQueTrava && (
            <Text size="sm" c="dimmed">
              {d.oQueTrava}
            </Text>
          )}
        </div>
        <Group gap="xs" style={{ flexShrink: 0 }}>
          {formatValor(d.valorEmJogo) && (
            <Text fw={600} size="sm">
              {formatValor(d.valorEmJogo)}
            </Text>
          )}
          <Badge>{d.nivel}</Badge>
          <Badge variant="light">{d.status}</Badge>
          {emJanela && <Countdown prazo={d.prazo} onVencido={invalidar} />}
        </Group>
      </Group>

      <Text size="sm" mt={4}>
        {d.proposta ? `Proposta: ${d.proposta}` : "Sem proposta registrada."}
      </Text>
      <Text size="xs" c="dimmed">
        {d.quemEspera ? `espera: ${d.quemEspera} · ` : ""}
        {contratoDoSilencio(d)}
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

      <Group mt="xs">
        <Button
          size="xs"
          variant="subtle"
          px={0}
          onClick={() => setVerTrilha((v) => !v)}
          aria-expanded={verTrilha}
        >
          {verTrilha ? "Ocultar trilha" : "Ver trilha"}
        </Button>
      </Group>
      <Collapse expanded={verTrilha}>
        <Stack gap={4} mt="sm" pt="xs" style={{ borderTop: "1px solid var(--mantine-color-gray-2)" }}>
          <TrilhaTimeline pendenciaId={d.pendenciaId} enabled={verTrilha} />
        </Stack>
      </Collapse>
    </Paper>
  );
}
