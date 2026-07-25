import { Badge, Button, Collapse, Group, Paper, Select, SimpleGrid, Stack, Text, TextInput, Title } from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  criarRegra,
  desativar,
  getPropostas,
  getRegras,
  silenciar,
  type PropostaRegra,
} from "../api/regras";
import type { Nivel } from "../api/types";
import { formatValor } from "../util/formato";
import { PageHeader } from "./PageHeader";
import { TrilhaTimeline } from "./TrilhaTimeline";

const PROPOSTAS = ["regras-propostas"] as const;
const ATIVAS = ["regras-ativas"] as const;

export function AlcadasPage() {
  const { data: propostas } = useQuery({ queryKey: PROPOSTAS, queryFn: getPropostas });
  const { data: ativas } = useQuery({ queryKey: ATIVAS, queryFn: getRegras });
  const qc = useQueryClient();
  const invalidar = () => {
    qc.invalidateQueries({ queryKey: PROPOSTAS });
    qc.invalidateQueries({ queryKey: ATIVAS });
  };

  return (
    <Stack>
      <PageHeader
        titulo="Alçadas"
        sub="Regras de autonomia mineradas do seu histórico. Nada vira regra sem você aceitar (INV-10)."
      />

      <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="md" style={{ alignItems: "start" }}>
        <div>
          <Stack gap="xs">
            <Title order={6}>Propostas</Title>
            {(propostas ?? []).map((p) => (
              <CardProposta key={p.classe} p={p} aoAgir={invalidar} />
            ))}
            {propostas && propostas.length === 0 && (
              <Text size="sm" c="dimmed">Nenhum padrão consistente o suficiente para virar regra ainda.</Text>
            )}
          </Stack>
        </div>
        <div>
          <Stack gap="xs">
            <Title order={6}>Regras ativas</Title>
            {(ativas ?? []).map((r) => (
              <Paper key={r.id} withBorder p="sm">
                <Group justify="space-between">
                  <Text size="sm">
                    <b>{r.classe}</b> roteia automaticamente em <Badge size="xs">{r.nivel}</Badge>
                  </Text>
                  <BotaoDesativar id={r.id} aoAgir={invalidar} />
                </Group>
              </Paper>
            ))}
            {ativas && ativas.length === 0 && (
              <Text size="sm" c="dimmed">Nenhuma regra ativa. Tudo ainda passa por você.</Text>
            )}
          </Stack>
        </div>
      </SimpleGrid>
    </Stack>
  );
}

function CardProposta({ p, aoAgir }: { p: PropostaRegra; aoAgir: () => void }) {
  const [nivel, setNivel] = useState<Nivel>(p.nivelSugerido);
  const [dono, setDono] = useState(p.donoSugerido ?? "");
  const [verCasos, setVerCasos] = useState(false);
  const mAceitar = useMutation({
    mutationFn: () => criarRegra({ classe: p.classe, nivel, donoId: dono }),
    onSuccess: aoAgir,
  });
  const mSilenciar = useMutation({ mutationFn: () => silenciar(p.classe), onSuccess: aoAgir });

  return (
    <Paper withBorder p="md" data-testid={`proposta-${p.classe}`}>
      <Group justify="space-between">
        <Text fw={500}>
          <b>{p.classe}</b> — {p.ocorrencias} decisões, {Math.round(p.consistencia * 100)}% consistentes,
          zero reversões
        </Text>
        <Badge variant="light">sugerido {p.nivelSugerido}</Badge>
      </Group>
      <Text size="xs" c="dimmed" mt={2}>
        Vira regra de autonomia: novas capturas desta classe seriam roteadas sozinhas.
      </Text>

      <Button size="xs" variant="subtle" px={0} mt="xs" onClick={() => setVerCasos((v) => !v)}
        aria-expanded={verCasos}>
        {verCasos ? "Ocultar evidência" : `Ver evidência (${p.casos.length} casos)`}
      </Button>
      <Collapse expanded={verCasos}>
        <Stack gap={4} mt="xs">
          {p.casos.map((c) => (
            <CasoLinha key={c.pendenciaId} pendenciaId={c.pendenciaId} titulo={c.titulo}
              desfecho={c.desfecho} valor={c.valorEmJogo} />
          ))}
        </Stack>
      </Collapse>

      <Group mt="sm" gap="xs" align="flex-end">
        <Select label="Nível" data={["N1", "N2", "N3"]} value={nivel}
          onChange={(v) => setNivel((v as Nivel) ?? "N1")} w={90} size="xs" />
        <TextInput label="Dono (id)" placeholder="id da pessoa" value={dono}
          onChange={(e) => setDono(e.currentTarget.value)} size="xs" style={{ flex: 1 }} />
        <Button size="xs" onClick={() => mAceitar.mutate()} disabled={!dono}>Aceitar</Button>
        <Button size="xs" variant="default" onClick={() => mSilenciar.mutate()}>Silenciar</Button>
      </Group>
    </Paper>
  );
}

function CasoLinha({ pendenciaId, titulo, desfecho, valor }: {
  pendenciaId: string; titulo: string; desfecho: string | null; valor: number | null;
}) {
  const [aberto, setAberto] = useState(false);
  return (
    <div>
      <Group justify="space-between" wrap="nowrap">
        <Button variant="subtle" size="compact-xs" px={0} onClick={() => setAberto((v) => !v)}>
          {titulo}
        </Button>
        <Group gap="xs">
          {desfecho && <Badge size="xs" variant="light" color="teal">{desfecho}</Badge>}
          {formatValor(valor) && <Text size="xs" c="dimmed">{formatValor(valor)}</Text>}
        </Group>
      </Group>
      <Collapse expanded={aberto}>
        <div style={{ paddingLeft: 8 }}>
          <TrilhaTimeline pendenciaId={pendenciaId} enabled={aberto} />
        </div>
      </Collapse>
    </div>
  );
}

function BotaoDesativar({ id, aoAgir }: { id: string; aoAgir: () => void }) {
  const m = useMutation({ mutationFn: () => desativar(id), onSuccess: aoAgir });
  return (
    <Button size="xs" variant="default" onClick={() => m.mutate()}>Desativar</Button>
  );
}
