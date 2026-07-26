import { Alert, Badge, Button, Group, Paper, Radio, SegmentedControl, SimpleGrid, Stack, Text, Textarea, TextInput, Title } from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { decidir, getBloco, perguntarDossie, redigir, type RespostaDossie } from "../api/bloco";
import { rotulo } from "../util/rotulos";
import { TrilhaTimeline } from "./TrilhaTimeline";

export function BlocoPage() {
  const { id } = useParams({ from: "/bloco/$id" });
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ["bloco", id], queryFn: () => getBloco(id) });

  const [opcao, setOpcao] = useState<string | null>(null);
  const [tom, setTom] = useState("direto");
  const [texto, setTexto] = useState("");
  const [aviso, setAviso] = useState<string | null>(null);
  const [verTrilha, setVerTrilha] = useState(false);

  const mRedigir = useMutation({
    mutationFn: () => redigir(id, opcaoRotulo(), tom),
    onSuccess: (r) => {
      setTexto(r.rascunho);
      setAviso(r.disponivel ? null : r.aviso);
    },
  });
  const mDecidir = useMutation({
    mutationFn: () => decidir(id, opcaoRotulo(), texto),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["entrada"] });
      qc.invalidateQueries({ queryKey: ["hoje"] });
      navigate({ to: "/" });
    },
  });

  function opcaoRotulo() {
    return data?.opcoes.find((o) => o.chave === opcao)?.rotulo ?? opcao ?? "";
  }

  if (!data) return null;
  const d = data;

  return (
    <Stack>
      <div>
        <Title order={4}>{d.titulo}</Title>
        <Text size="xs" c="dimmed">Bloco de decisão · {d.classe}</Text>
      </div>

      <SimpleGrid cols={{ base: 1, lg: 3 }} spacing="md" style={{ alignItems: "start" }}>
      <Paper withBorder p="md">
        <Title order={6}>Dossiê</Title>
        <Text size="xs" c="dimmed" mb="sm">Fatos do item. Fonte: a trilha.</Text>
        <Stack gap={4}>
          {d.dossie.map((f) => (
            <Group key={f.rotulo} justify="space-between">
              <Text size="sm" c="dimmed">{f.rotulo}</Text>
              <Text size="sm" fw={500}>{f.valor}</Text>
            </Group>
          ))}
        </Stack>
        <Button size="xs" variant="subtle" px={0} mt="xs" onClick={() => setVerTrilha((v) => !v)}>
          {verTrilha ? "Ocultar trilha" : "Ver trilha (fonte)"}
        </Button>
        {verTrilha && <div style={{ marginTop: 8 }}><TrilhaTimeline pendenciaId={id} enabled={verTrilha} /></div>}
        <PerguntarDossie id={id} />
      </Paper>

      <Paper withBorder p="md">
        <Title order={6}>Opção e consequência</Title>
        <Radio.Group value={opcao} onChange={setOpcao}>
          <Stack gap="xs" mt="xs">
            {d.opcoes.map((o) => (
              <Radio key={o.chave} value={o.chave} label={
                <span><b>{o.rotulo}</b> <Text span size="xs" c="dimmed">— {o.consequencia}</Text></span>
              } />
            ))}
          </Stack>
        </Radio.Group>
      </Paper>

      <Paper withBorder p="md">
        <Title order={6}>Comunicar a decisão</Title>
        <Group gap="xs" mt="xs" mb="xs">
          <SegmentedControl size="xs" data={[{ value: "direto", label: "Direto" }, { value: "diplomatico", label: "Diplomático" }]}
            value={tom} onChange={setTom} />
          <Button size="xs" variant="light" disabled={!opcao} onClick={() => mRedigir.mutate()}>
            Gerar rascunho
          </Button>
        </Group>
        {aviso && <Alert color="yellow" mb="xs" data-testid="aviso-modelo">{aviso}</Alert>}
        <Textarea rows={5} placeholder="o texto que vai ao canal de origem"
          value={texto} onChange={(e) => setTexto(e.currentTarget.value)} />
        <Text size="xs" c="dimmed" mt={6}>
          O texto sai no canal de origem, a decisão vai para a trilha e quem esperava é avisado.
        </Text>
        <Group justify="flex-end" mt="sm">
          <Button disabled={!opcao} onClick={() => mDecidir.mutate()}>Decidir e comunicar</Button>
        </Group>
      </Paper>
      </SimpleGrid>
    </Stack>
  );
}

/** Pergunta ao dossiê (RFC-0004 §1): recuperação com fonte; "não encontrei" sem inventar. */
function PerguntarDossie({ id }: { id: string }) {
  const [pergunta, setPergunta] = useState("");
  const [resp, setResp] = useState<RespostaDossie | null>(null);
  const m = useMutation({
    mutationFn: () => perguntarDossie(id, pergunta),
    onSuccess: (r) => setResp(r),
  });
  return (
    <Stack gap={6} mt="md" pt="sm" style={{ borderTop: "1px solid var(--mantine-color-gray-2)" }}>
      <Text size="xs" fw={600}>Perguntar ao dossiê</Text>
      <Group gap="xs">
        <TextInput size="xs" style={{ flex: 1 }} placeholder="ex.: já foi reprovado antes?"
          value={pergunta} onChange={(e) => setPergunta(e.currentTarget.value)}
          onKeyDown={(e) => e.key === "Enter" && pergunta && m.mutate()} />
        <Button size="xs" variant="light" disabled={!pergunta} onClick={() => m.mutate()}>Perguntar</Button>
      </Group>
      {resp && !resp.encontrou && (
        <Text size="sm" c="dimmed" data-testid="dossie-vazio">Não encontrei isso na base.</Text>
      )}
      {resp && resp.encontrou && (
        <Stack gap={4} data-testid="dossie-resposta">
          {resp.correcao && (
            <Alert color="orange" p="xs" data-testid="dossie-correcao">
              <Text size="sm" fw={500}>{resp.correcao}</Text>
            </Alert>
          )}
          <Text size="sm" style={{ whiteSpace: "pre-wrap" }}>{resp.resposta}</Text>
          <Group gap={4}>
            {resp.fontes.map((f, i) => (
              <Badge key={i} size="xs" variant="light" color="blue">{rotulo(f.fonteTipo)}</Badge>
            ))}
          </Group>
        </Stack>
      )}
    </Stack>
  );
}
