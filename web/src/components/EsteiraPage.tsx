import { Badge, Button, Collapse, Group, Paper, SegmentedControl, Select, Stack, Text, TextInput, Title } from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  avaliar,
  criarInstancia,
  getChecklist,
  getEsteiras,
  getInstancias,
  getPropostasChecklist,
  publicarChecklist,
  type Checklist,
  type Instancia,
} from "../api/esteira";

export function EsteiraPage() {
  const { data: esteiras } = useQuery({ queryKey: ["esteiras"], queryFn: getEsteiras });
  const esteira = (esteiras ?? [])[0];

  if (!esteiras) return null;
  if (!esteira) {
    return (
      <Stack>
        <Title order={4}>Esteira</Title>
        <Text c="dimmed" size="sm">Nenhuma esteira configurada.</Text>
      </Stack>
    );
  }

  return (
    <Stack>
      <div>
        <Title order={4}>Esteira — {esteira.nome}</Title>
        <Text size="xs" c="dimmed">
          {esteira.etapas.map((e) => e.nome).join(" → ")}
        </Text>
      </div>
      <NovaInstancia esteiraId={esteira.id} />
      <Instancias esteiraId={esteira.id} />
      <Propostas esteiraId={esteira.id} />
    </Stack>
  );
}

function NovaInstancia({ esteiraId }: { esteiraId: string }) {
  const [nome, setNome] = useState("");
  const qc = useQueryClient();
  const m = useMutation({
    mutationFn: () => criarInstancia(esteiraId, nome),
    onSuccess: () => {
      setNome("");
      qc.invalidateQueries({ queryKey: ["instancias", esteiraId] });
    },
  });
  return (
    <Group gap="xs">
      <TextInput placeholder="nova instância (entidade externa)" value={nome} size="xs"
        onChange={(e) => setNome(e.currentTarget.value)} style={{ flex: 1 }} />
      <Button size="xs" disabled={!nome} onClick={() => m.mutate()}>Adicionar</Button>
    </Group>
  );
}

function Instancias({ esteiraId }: { esteiraId: string }) {
  const { data } = useQuery({ queryKey: ["instancias", esteiraId], queryFn: () => getInstancias(esteiraId) });
  const { data: checklist } = useQuery({ queryKey: ["checklist", esteiraId], queryFn: () => getChecklist(esteiraId) });
  return (
    <Stack gap="xs">
      <Title order={6}>Instâncias</Title>
      {(data ?? []).map((i) => (
        <CardInstancia key={i.id} esteiraId={esteiraId} i={i} checklist={checklist} />
      ))}
      {data && data.length === 0 && <Text size="sm" c="dimmed">Nenhuma instância ainda.</Text>}
    </Stack>
  );
}

function CardInstancia({ esteiraId, i, checklist }: { esteiraId: string; i: Instancia; checklist?: Checklist }) {
  const qc = useQueryClient();
  const [aberto, setAberto] = useState(false);
  const [res, setRes] = useState<Record<string, string>>({});
  const [apTexto, setApTexto] = useState("");
  const [apTipo, setApTipo] = useState("OBJETIVO");
  const [desfecho, setDesfecho] = useState<string | null>(null);

  const m = useMutation({
    mutationFn: () =>
      avaliar(
        i.id,
        (checklist?.criterios ?? []).map((c) => ({ criterioChave: c.chave, resultado: res[c.chave] ?? "OK" })),
        apTexto ? [{ texto: apTexto, tipo: apTipo }] : [],
      ),
    onSuccess: (r) => {
      setDesfecho(r.desfecho);
      setApTexto("");
      qc.invalidateQueries({ queryKey: ["instancias", esteiraId] });
      qc.invalidateQueries({ queryKey: ["hoje"] });
    },
  });

  return (
    <Paper withBorder p="sm">
      <Group justify="space-between">
        <Text size="sm" fw={500}>{i.entidadeExterna}</Text>
        <Group gap="xs">
          <Badge size="xs" variant="light">{i.etapaAtualNome ?? i.status}</Badge>
          {i.status === "EM_ANDAMENTO" && (
            <Button size="compact-xs" variant="subtle" onClick={() => setAberto((v) => !v)}>Avaliar</Button>
          )}
        </Group>
      </Group>
      {desfecho && <Text size="xs" c={desfecho === "APROVADA" ? "teal" : "orange"}>desfecho: {desfecho}</Text>}
      <Collapse expanded={aberto}>
        <Stack gap={6} mt="xs">
          {(checklist?.criterios ?? []).map((c) => (
            <Group key={c.chave} justify="space-between">
              <Text size="xs">{c.descricao} <Badge size="xs" variant="light" color="gray">{c.tipo}</Badge></Text>
              <SegmentedControl size="xs" data={["OK", "FALHOU", "NAO_APLICA"]}
                value={res[c.chave] ?? "OK"} onChange={(v) => setRes((s) => ({ ...s, [c.chave]: v }))} />
            </Group>
          ))}
          <Group gap="xs">
            <TextInput placeholder="apontamento (motivo)" value={apTexto} size="xs" style={{ flex: 1 }}
              onChange={(e) => setApTexto(e.currentTarget.value)} />
            <Select size="xs" w={130} data={["OBJETIVO", "JULGAMENTO"]} value={apTipo}
              onChange={(v) => setApTipo(v ?? "OBJETIVO")} />
          </Group>
          <Button size="xs" onClick={() => m.mutate()}>Registrar avaliação</Button>
        </Stack>
      </Collapse>
    </Paper>
  );
}

function Propostas({ esteiraId }: { esteiraId: string }) {
  const { data } = useQuery({ queryKey: ["checklist-propostas", esteiraId], queryFn: () => getPropostasChecklist(esteiraId) });
  const { data: checklist } = useQuery({ queryKey: ["checklist", esteiraId], queryFn: () => getChecklist(esteiraId) });
  const qc = useQueryClient();
  const aceitar = useMutation({
    mutationFn: (cand: { chave: string; descricao: string }) =>
      publicarChecklist(esteiraId, [
        ...(checklist?.criterios ?? []),
        { chave: cand.chave, descricao: cand.descricao, tipo: "OBJETIVO", obrigatorio: true },
      ]),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["checklist", esteiraId] });
      qc.invalidateQueries({ queryKey: ["checklist-propostas", esteiraId] });
    },
  });

  if (!data || (data.objetivos.length === 0 && data.julgamento.length === 0)) return null;
  return (
    <Paper withBorder p="md">
      <Title order={6}>Checklist — o que se repete nas reprovações</Title>
      <Text size="xs" c="dimmed" mb="xs">Critérios objetivos recorrentes viram checklist; julgamento fica com você.</Text>
      <Stack gap="xs">
        {data.objetivos.map((o) => (
          <Group key={o.chave} justify="space-between">
            <Text size="sm">{o.descricao} <Badge size="xs" color="orange">{Math.round(o.fracao * 100)}% das reprovações</Badge></Text>
            <Button size="xs" onClick={() => aceitar.mutate(o)}>Virar critério</Button>
          </Group>
        ))}
        {data.julgamento.map((j) => (
          <Group key={j} justify="space-between">
            <Text size="sm" c="dimmed">{j}</Text>
            <Badge size="xs" variant="light">julgamento — fica com você</Badge>
          </Group>
        ))}
      </Stack>
    </Paper>
  );
}
