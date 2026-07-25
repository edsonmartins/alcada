import { Badge, Button, Collapse, Group, Paper, Stack, Text } from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { getPerguntas, responder, type PerguntaAprendizado, type Resposta } from "../api/aprendizado";
import { getHoje } from "../api/pendencias";
import { PageHeader } from "./PageHeader";
import { TrilhaTimeline } from "./TrilhaTimeline";

/** Hoje: no máximo 3 (o backend já limita; a UI reforça). Nada contemplativo. */
export function HojePage() {
  const { data } = useQuery({ queryKey: ["hoje"], queryFn: getHoje });
  const itens = (data ?? []).slice(0, 3);
  const navigate = useNavigate();

  return (
    <Stack>
      <PerguntaAprendizadoCard />
      <PageHeader titulo="Hoje" sub="No máximo três. Nada contemplativo — o que precisa de você agora." />
      {itens.map((h) => (
        <Paper
          key={h.id}
          withBorder
          p="sm"
          data-testid="item-hoje"
          onClick={() => navigate({ to: "/bloco/$id", params: { id: h.id } })}
          style={{ cursor: "pointer" }}
        >
          <Group justify="space-between" wrap="nowrap" align="flex-start">
            <div style={{ minWidth: 0 }}>
              <Text fw={500}>{h.titulo}</Text>
              <Text size="xs" c="dimmed">
                por quê: {h.justificativa}
              </Text>
            </div>
            <Button
              size="xs"
              variant="light"
              style={{ flexShrink: 0 }}
              onClick={(e) => {
                e.stopPropagation();
                navigate({ to: "/bloco/$id", params: { id: h.id } });
              }}
            >
              Abrir bloco
            </Button>
          </Group>
        </Paper>
      ))}
      {itens.length === 0 && (
        <Text c="dimmed" ta="center" py="xl">
          Nada para hoje.
        </Text>
      )}
    </Stack>
  );
}

/**
 * Laço de aprendizado (ADR-0019): uma pergunta situada por vez, com evidência
 * clicável, e as três respostas. Só aparece quando há padrão candidato.
 */
function PerguntaAprendizadoCard() {
  const { data } = useQuery({ queryKey: ["aprendizado"], queryFn: getPerguntas });
  const p: PerguntaAprendizado | undefined = (data ?? [])[0];
  const qc = useQueryClient();
  const [verCasos, setVerCasos] = useState(false);
  const invalidar = () => {
    qc.invalidateQueries({ queryKey: ["aprendizado"] });
    qc.invalidateQueries({ queryKey: ["regras-ativas"] });
    qc.invalidateQueries({ queryKey: ["regras-propostas"] });
  };
  const m = useMutation({
    mutationFn: (r: Resposta) => responder(p!.id, r),
    onSuccess: invalidar,
  });

  if (!p) return null;

  return (
    <Paper withBorder p="md" data-testid="pergunta-aprendizado" style={{ borderColor: "var(--mantine-color-blue-4)" }}>
      <Group gap={6}>
        <Badge variant="light">aprendizado</Badge>
        <Text fw={500}>
          As decisões de <b>{p.classe}</b> viraram rotina ({p.ocorrencias} casos, sem reversões).
        </Text>
      </Group>
      <Text size="sm" c="dimmed" mt={2}>
        Transformar em regra de autonomia ({p.nivelSugerido})? Novas capturas dessa classe seriam
        roteadas sozinhas.
      </Text>

      <Button size="xs" variant="subtle" px={0} mt="xs" onClick={() => setVerCasos((v) => !v)}
        aria-expanded={verCasos}>
        {verCasos ? "Ocultar evidência" : `Ver evidência (${p.casos.length} casos)`}
      </Button>
      <Collapse expanded={verCasos}>
        <Stack gap={4} mt="xs">
          {p.casos.map((c) => (
            <CasoLinha key={c.pendenciaId} pendenciaId={c.pendenciaId} titulo={c.titulo} desfecho={c.desfecho} />
          ))}
        </Stack>
      </Collapse>

      <Group gap="xs" mt="sm">
        <Button size="xs" onClick={() => m.mutate("SIM")}>Sim, criar regra</Button>
        <Button size="xs" variant="default" onClick={() => m.mutate("AGORA_NAO")}>Agora não</Button>
        <Button size="xs" variant="subtle" color="gray" onClick={() => m.mutate("NAO_PERGUNTAR")}>
          Não perguntar isso
        </Button>
      </Group>
    </Paper>
  );
}

function CasoLinha({ pendenciaId, titulo, desfecho }: { pendenciaId: string; titulo: string; desfecho: string | null }) {
  const [aberto, setAberto] = useState(false);
  return (
    <div>
      <Group justify="space-between" wrap="nowrap">
        <Button variant="subtle" size="compact-xs" px={0} onClick={() => setAberto((v) => !v)}>
          {titulo}
        </Button>
        {desfecho && <Badge size="xs" variant="light" color="teal">{desfecho}</Badge>}
      </Group>
      <Collapse expanded={aberto}>
        <div style={{ paddingLeft: 8 }}>
          <TrilhaTimeline pendenciaId={pendenciaId} enabled={aberto} />
        </div>
      </Collapse>
    </div>
  );
}
