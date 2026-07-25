import { Badge, Button, Group, Paper, Progress, SimpleGrid, Stack, Text, Title } from "@mantine/core";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { getRevisao } from "../api/metricas";
import { PageHeader } from "./PageHeader";

export function SextaPage() {
  const { data } = useQuery({ queryKey: ["revisao-semanal"], queryFn: getRevisao });
  const [passo, setPasso] = useState(0);
  if (!data) return null;
  const d = data;

  const passos = [
    {
      titulo: "1. A fila de entrada",
      sub: "O que ainda não foi triado. Esvaziar, não organizar.",
      corpo: (
        <Stack gap="xs">
          <Text size="sm">{d.entrada.qtd} item(s) na entrada.</Text>
          {d.entrada.itens.map((i) => (
            <Paper key={i.id} withBorder p="xs">
              <Text size="sm" fw={500}>{i.titulo}</Text>
              {i.quemEspera && <Text size="xs" c="dimmed">espera: {i.quemEspera}</Text>}
            </Paper>
          ))}
          {d.entrada.qtd === 0 && <Text size="sm" c="dimmed">Entrada limpa. 👏</Text>}
        </Stack>
      ),
    },
    {
      titulo: "2. Adiados 3× ou mais",
      sub: "Diagnóstico, não priorização. Resolver, soltar ou matar.",
      corpo: (
        <Stack gap="xs">
          {d.adiados.map((a) => (
            <Paper key={a.id} withBorder p="xs">
              <Group gap={6}>
                <Text size="sm" fw={500}>{a.titulo}</Text>
                <Badge size="xs" color="orange">{a.adiadoCount}× adiado</Badge>
              </Group>
              {a.oQueTrava && <Text size="xs" c="dimmed">{a.oQueTrava}</Text>}
            </Paper>
          ))}
          {d.adiados.length === 0 && <Text size="sm" c="dimmed">Nada adiado três vezes ou mais.</Text>}
        </Stack>
      ),
    },
    {
      titulo: "3. O que pode virar regra",
      sub: "Dica de repetição — não é regra automática (a mineração vem depois).",
      corpo: (
        <Stack gap="xs">
          {d.podeVirarRegra.map((r) => (
            <Paper key={r.classe} withBorder p="xs">
              <Text size="sm">
                <b>{r.classe}</b> — {r.ocorrencias} decisões resolvidas nas últimas 4 semanas.
              </Text>
              <Text size="xs" c="dimmed">Candidata a regra de autonomia. Reveja em /alcadas.</Text>
            </Paper>
          ))}
          {d.podeVirarRegra.length === 0 && (
            <Text size="sm" c="dimmed">Nenhum padrão repetido o suficiente ainda.</Text>
          )}
        </Stack>
      ),
    },
    {
      titulo: "4. Resumo da semana",
      sub: "O que aconteceu desde segunda.",
      corpo: (
        <SimpleGrid cols={{ base: 2, sm: 3 }}>
          <Contador v={d.resumoSemana.resolvidas} r="resolvidas" />
          <Contador v={d.resumoSemana.executadas} r="executadas (N2)" />
          <Contador v={d.resumoSemana.delegadas} r="delegadas" />
          <Contador v={d.resumoSemana.escaladas} r="escaladas" />
          <Contador v={d.resumoSemana.devolvidas} r="devolvidas" />
          <Contador v={d.resumoSemana.fechadas} r="fechadas" />
        </SimpleGrid>
      ),
    },
  ];

  const atual = passos[passo];
  return (
    <Stack>
      <PageHeader titulo="Revisão de sexta" sub="Roteiro de ~20 minutos. Um passo de cada vez." />
      <Progress value={((passo + 1) / passos.length) * 100} size="sm" />

      <Paper withBorder p="md">
        <Title order={5}>{atual.titulo}</Title>
        <Text size="xs" c="dimmed" mb="sm">{atual.sub}</Text>
        {atual.corpo}
      </Paper>

      <Group justify="space-between">
        <Button variant="default" disabled={passo === 0} onClick={() => setPasso((p) => p - 1)}>
          Anterior
        </Button>
        <Text size="xs" c="dimmed">{passo + 1} / {passos.length}</Text>
        <Button disabled={passo === passos.length - 1} onClick={() => setPasso((p) => p + 1)}>
          Próximo
        </Button>
      </Group>
    </Stack>
  );
}

function Contador({ v, r }: { v: number; r: string }) {
  return (
    <Paper withBorder p="sm">
      <Text fz={26} lh={1} style={{ fontFamily: "'Bricolage Grotesque',sans-serif", fontWeight: 800, letterSpacing: "-.03em" }}>{v}</Text>
      <Text size="xs" c="dimmed" mt={4}>{r}</Text>
    </Paper>
  );
}
