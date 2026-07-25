import { Alert, Badge, Box, Button, Group, Paper, Stack, Text } from "@mantine/core";
import { useEffect, useRef, useState } from "react";
import type { SaidaDireta } from "../api/types";
import { useUI } from "../store/ui";
import { useTriagem } from "../triagem/useTriagem";
import { useTriagemKeys } from "../triagem/useTriagemKeys";
import { DrawerDetalhe } from "./DrawerDetalhe";

const LIMITE_IMPRODUTIVA_MS = 60_000;

const ROTULO: Record<SaidaDireta, string> = {
  resolver: "resolvido",
  reservar: "reservado",
  repousar: "adormecido",
};

export function EntradaPage() {
  const { itens, pendentes, aplicar, aplicarLote, desfazer } = useTriagem();
  const { cursor, selecao, setCursor } = useUI();

  // Sessão improdutiva (ADR-0018): uso sem nenhuma transição.
  const transicoes = useRef(0);
  const [improdutiva, setImprodutiva] = useState(false);
  useEffect(() => {
    const t = setTimeout(() => {
      if (transicoes.current === 0) setImprodutiva(true);
    }, LIMITE_IMPRODUTIVA_MS);
    return () => clearTimeout(t);
  }, []);

  function comContagem(fn: () => void) {
    transicoes.current += 1;
    setImprodutiva(false);
    fn();
  }
  const aplicarC = (id: string, s: SaidaDireta) => comContagem(() => aplicar(id, s));
  const aplicarLoteC = (ids: string[], s: SaidaDireta) => comContagem(() => aplicarLote(ids, s));

  useTriagemKeys({ itens, aplicar: aplicarC, aplicarLote: aplicarLoteC });

  return (
    <Box>
      {improdutiva && (
        <Alert color="yellow" mb="sm" data-testid="sessao-improdutiva">
          Você está aqui há um tempo sem decidir nada. Esta lista é para esvaziar, não para organizar.
        </Alert>
      )}

      {pendentes.map((p) => (
        <Paper key={p.token} withBorder p="xs" mb="xs" data-testid="desfazer-barra">
          <Group justify="space-between">
            <Text size="sm">
              {p.ids.length} {ROTULO[p.saida]} — desfaz em segundos
            </Text>
            <Button size="xs" variant="subtle" onClick={() => desfazer(p.token)}>
              Desfazer
            </Button>
          </Group>
        </Paper>
      ))}

      {selecao.size > 0 && (
        <Paper withBorder p="xs" mb="xs" data-testid="barra-lote">
          <Group justify="space-between">
            <Text size="sm">{selecao.size} selecionados</Text>
            <Group gap="xs">
              <Button size="xs" onClick={() => { aplicarLoteC([...selecao], "resolver"); useUI.getState().limparSelecao(); }}>
                Resolver (1)
              </Button>
              <Button size="xs" variant="default" onClick={() => { aplicarLoteC([...selecao], "repousar"); useUI.getState().limparSelecao(); }}>
                Repousar (4)
              </Button>
            </Group>
          </Group>
        </Paper>
      )}

      <Stack gap={4} role="list" aria-label="entrada">
        {itens.map((p, i) => (
          <Paper
            key={p.id}
            withBorder
            p="sm"
            role="listitem"
            data-testid={`item-${p.id}`}
            data-cursor={i === cursor ? "true" : undefined}
            aria-selected={selecao.has(p.id)}
            onClick={() => setCursor(i)}
            style={{ outline: i === cursor ? "2px solid var(--mantine-color-dark-4)" : undefined, cursor: "pointer" }}
          >
            <Group justify="space-between" wrap="nowrap">
              <div>
                <Text fw={500}>{p.titulo}</Text>
                <Text size="xs" c="dimmed">
                  {p.quemEspera ? `espera: ${p.quemEspera}` : "—"} · próxima ação: ↵ abrir · 1–4 decidir
                </Text>
              </div>
              <Group gap="xs">
                {selecao.has(p.id) && <Badge size="xs" data-testid="selecionado">no lote</Badge>}
                {p.temperatura > 0 && <Badge size="xs" color="orange">{p.temperatura}×</Badge>}
                {p.baixaConfianca && <Badge size="xs" color="gray">rever</Badge>}
              </Group>
            </Group>
          </Paper>
        ))}
        {itens.length === 0 && (
          <Text c="dimmed" ta="center" py="xl">
            Entrada vazia. Nada depende de você agora.
          </Text>
        )}
      </Stack>

      <DrawerDetalhe />
    </Box>
  );
}
