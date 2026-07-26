import { Badge, Button, Group, Paper, SimpleGrid, Stack, Text, Title } from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getRadar, type ItemAdiado } from "../api/metricas";
import { aplicarSaida } from "../api/pendencias";
import { formatValor } from "../util/formato";
import { ConsultaBox } from "./ConsultaBox";
import { PageHeader } from "./PageHeader";

export function RadarPage() {
  const { data } = useQuery({ queryKey: ["radar"], queryFn: getRadar });
  if (!data) return null;
  const d = data;

  return (
    <Stack>
      <PageHeader titulo="Radar de gargalo" sub="Diagnóstico organizacional — não placar pessoal." />

      <ConsultaBox />

      <SimpleGrid cols={{ base: 2, sm: 4 }}>
        <Metrica valor={`${d.dependeDoGestor.pct}%`} rotulo="trava em você"
          nota={`${d.dependeDoGestor.qtd} de ${d.dependeDoGestor.total} itens abertos`} />
        <Metrica valor={d.rodandoSemVoce} rotulo="rodando sem você" nota="N1 e N2 ativos" />
        <Metrica valor={d.adiados.length} rotulo="adiados 3× ou mais" nota="resolver, soltar ou matar" />
        <Metrica valor={d.piorEspera ? `${d.piorEspera.dias}d` : "—"} rotulo="pior espera"
          nota={d.piorEspera?.quemEspera ?? "ninguém esperando"} />
      </SimpleGrid>

      <Paper withBorder p="md">
        <Title order={6}>Itens adiados 3 vezes ou mais</Title>
        <Text size="xs" c="dimmed" mb="sm">
          Adiamento repetido não é priorização — é diagnóstico. A pergunta é: resolver, soltar ou matar?
        </Text>
        <Stack gap="xs">
          {d.adiados.map((a) => (
            <ItemAdiadoLinha key={a.id} a={a} />
          ))}
          {d.adiados.length === 0 && (
            <Text size="sm" c="dimmed">
              Nenhum item adiado três vezes ou mais.
            </Text>
          )}
        </Stack>
      </Paper>

      <Paper withBorder p="md">
        <Title order={6}>Itens que dependem de você — últimas 8 semanas</Title>
        <Text size="xs" c="dimmed" mb="sm">
          Entradas × fechamentos por semana. Se as entradas não caem abaixo dos fechamentos, o gargalo
          não encolhe (INV-01).
        </Text>
        <Encolhimento serie={d.encolhimento} />
      </Paper>

      <SimpleGrid cols={{ base: 1, sm: 2 }}>
        <Paper withBorder p="md">
          <Title order={6}>Autonomia (90 dias)</Title>
          <Text size="xs" c="dimmed" mb="xs">Contados separadamente — não somar (ADR-0024).</Text>
          <ContagemLinha rotulo="Executadas por você" v={d.autonomia.deliberada} />
          <ContagemLinha rotulo="Executadas por ausência" v={d.autonomia.porAusencia} />
          <ContagemLinha rotulo="Devolvidas pelo executor" v={d.autonomia.devolvida} />
          <ContagemLinha rotulo="Escaladas" v={d.autonomia.escalada} />
          <ContagemLinha rotulo="Nível promovido" v={d.autonomia.promovida} />
        </Paper>
        <Paper withBorder p="md">
          <Title order={6}>Fechamento no canal (90 dias)</Title>
          <Text size="xs" c="dimmed" mb="xs">Entregue × falho × impossível (ADR-0025).</Text>
          <ContagemLinha rotulo="Entregue" v={d.fechamentoCanal.entregue} />
          <ContagemLinha rotulo="Falha de comunicação" v={d.fechamentoCanal.falho} />
          <ContagemLinha rotulo="Comunicação impossível" v={d.fechamentoCanal.impossivel} />
        </Paper>
      </SimpleGrid>
    </Stack>
  );
}

function Metrica({ valor, rotulo, nota }: { valor: string | number; rotulo: string; nota: string }) {
  return (
    <Paper withBorder p="md">
      <Text fz={30} lh={1} style={{ fontFamily: "'Bricolage Grotesque',sans-serif", fontWeight: 800, letterSpacing: "-.03em" }}>{valor}</Text>
      <Text size="sm">{rotulo}</Text>
      <Text size="xs" c="dimmed">{nota}</Text>
    </Paper>
  );
}

function ContagemLinha({ rotulo, v }: { rotulo: string; v: number }) {
  return (
    <Group justify="space-between" py={2}>
      <Text size="sm" c="dimmed">{rotulo}</Text>
      <Text fw={600} size="sm">{v}</Text>
    </Group>
  );
}

function ItemAdiadoLinha({ a }: { a: ItemAdiado }) {
  const qc = useQueryClient();
  const invalidar = () => qc.invalidateQueries({ queryKey: ["radar"] });
  const mResolver = useMutation({ mutationFn: () => aplicarSaida(a.id, "resolver"), onSuccess: invalidar });
  const mSoltar = useMutation({ mutationFn: () => aplicarSaida(a.id, "repousar"), onSuccess: invalidar });
  return (
    <Group justify="space-between" wrap="nowrap">
      <div style={{ minWidth: 0 }}>
        <Group gap={6}>
          <Text fw={500} size="sm">{a.titulo}</Text>
          <Badge size="xs" color="orange">{a.adiadoCount}× adiado</Badge>
        </Group>
        {a.oQueTrava && <Text size="xs" c="dimmed">{a.oQueTrava}</Text>}
      </div>
      <Group gap="xs" wrap="nowrap" style={{ flexShrink: 0 }}>
        {formatValor(a.valorEmJogo) && <Text size="sm" fw={600}>{formatValor(a.valorEmJogo)}</Text>}
        <Button size="xs" variant="light" onClick={() => mResolver.mutate()}>Resolver</Button>
        <Button size="xs" variant="default" onClick={() => mSoltar.mutate()}>Soltar</Button>
      </Group>
    </Group>
  );
}

function Encolhimento({ serie }: { serie: Array<{ semana: string; entraram: number; fecharam: number }> }) {
  const max = Math.max(1, ...serie.flatMap((s) => [s.entraram, s.fecharam]));
  return (
    <Group align="flex-end" gap="sm" style={{ height: 120 }}>
      {serie.map((s) => (
        <Stack key={s.semana} gap={2} align="center" style={{ flex: 1 }} title={s.semana}>
          <Group gap={2} align="flex-end" style={{ height: 90 }}>
            <div title={`entraram ${s.entraram}`}
              style={{ width: 10, height: `${(s.entraram / max) * 100}%`,
                background: "var(--mantine-color-red-4)", borderRadius: 2 }} />
            <div title={`fecharam ${s.fecharam}`}
              style={{ width: 10, height: `${(s.fecharam / max) * 100}%`,
                background: "var(--mantine-color-teal-5)", borderRadius: 2 }} />
          </Group>
          <Text size="9px" c="dimmed">{s.semana.slice(5)}</Text>
        </Stack>
      ))}
    </Group>
  );
}
