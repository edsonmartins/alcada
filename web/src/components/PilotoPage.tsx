import { Alert, Badge, Button, Group, NumberInput, Paper, SimpleGrid, Stack, Text, Textarea, Title } from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { avaliarDescarte, getAmostra, getRelatorio, reconciliar } from "../api/piloto";
import { PageHeader } from "./PageHeader";

const fim = new Date().toISOString();
const inicio = new Date(Date.now() - 14 * 86400_000).toISOString();

/** Instrumento administrativo de validação; não integra a navegação diária. */
export function PilotoPage() {
  const qc = useQueryClient();
  const { data, error } = useQuery({ queryKey: ["piloto", inicio, fim], queryFn: () => getRelatorio(inicio, fim) });
  const { data: descartes = [] } = useQuery({ queryKey: ["piloto-amostra", inicio, fim], queryFn: () => getAmostra(inicio, fim) });
  const [fora, setFora] = useState<number | string>(0);
  const [obs, setObs] = useState("");
  const mRec = useMutation({ mutationFn: () => reconciliar(segundaAtual(), Number(fora), obs), onSuccess: () => qc.invalidateQueries({ queryKey: ["piloto"] }) });
  const mAval = useMutation({ mutationFn: ({ id, r }: { id: string; r: "ERA_PENDENCIA" | "NAO_ERA" | "INCONCLUSIVO" }) => avaliarDescarte(id, r), onSuccess: () => qc.invalidateQueries({ queryKey: ["piloto"] }) });

  if (error) return <Alert color="red">Não foi possível carregar o relatório do piloto.</Alert>;
  if (!data) return <Text c="dimmed">Carregando evidências do piloto…</Text>;
  return <Stack>
    <PageHeader titulo="Validação do piloto" sub="Evidência para G2/G7 — não é placar e não decide os gates sozinho." />
    <SimpleGrid cols={{ base: 2, sm: 4 }}>
      <Metrica v={`${data.autonomia.fracaoPct}%`} r="fração autônoma" />
      <Metrica v={data.n2.porAusencia} r="N2 por ausência" />
      <Metrica v={data.n2.intervencoes} r="intervenções N2" />
      <Metrica v={`${data.captura.escapePct}%`} r="escape conhecido" />
    </SimpleGrid>
    <Alert color="blue">{data.captura.aviso}</Alert>
    <Paper withBorder p="md"><Title order={6}>Desfechos N2 separados</Title>
      <Group mt="xs" gap="xs">{Object.entries(data.n2).map(([k,v]) => <Badge key={k} variant="light">{k}: {v}</Badge>)}</Group>
    </Paper>
    <Paper withBorder p="md"><Title order={6}>Reconciliação da semana</Title>
      <Text size="sm" c="dimmed">Quantas decisões reais aconteceram fora da fila? O registro não cria item.</Text>
      <Group align="flex-end" mt="xs"><NumberInput label="Decisões fora da fila" min={0} value={fora} onChange={setFora} />
        <Textarea label="Observação opcional" value={obs} onChange={(e) => setObs(e.currentTarget.value)} />
        <Button onClick={() => mRec.mutate()}>Registrar</Button></Group>
    </Paper>
    <Paper withBorder p="md"><Title order={6}>Amostra de descartes ainda retidos</Title>
      <Stack gap="xs" mt="xs">{descartes.map(d => <Paper key={d.id} withBorder p="xs">
        <Text size="xs" c="dimmed">{d.fonte} · {d.motivo}</Text><Text size="sm">{d.trecho}</Text>
        <Group gap="xs" mt={4}><Button size="compact-xs" onClick={() => mAval.mutate({id:d.id,r:"ERA_PENDENCIA"})}>Deveria entrar</Button>
          <Button size="compact-xs" variant="default" onClick={() => mAval.mutate({id:d.id,r:"NAO_ERA"})}>Não deveria</Button>
          <Button size="compact-xs" variant="subtle" onClick={() => mAval.mutate({id:d.id,r:"INCONCLUSIVO"})}>Inconclusivo</Button></Group>
      </Paper>)}</Stack>
    </Paper>
    <Paper withBorder p="md"><Title order={6}>Saúde das fontes</Title>{data.fontes.map(f => <Group key={f.id} justify="space-between" py={4}><Text size="sm">{f.nome}</Text><Badge color={f.acao === "OK" ? "teal" : "orange"}>{f.acao === "OK" ? "recebendo" : "testar fonte"}</Badge></Group>)}</Paper>
  </Stack>;
}

function Metrica({v,r}:{v:string|number;r:string}) { return <Paper withBorder p="md"><Text fz={28} fw={700}>{v}</Text><Text size="xs" c="dimmed">{r}</Text></Paper>; }
function segundaAtual() { const d=new Date(); const dia=d.getDay(); d.setDate(d.getDate()-(dia===0?6:dia-1)); return d.toISOString().slice(0,10); }
