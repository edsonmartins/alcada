import { Alert, Anchor, Badge, Box, Button, Chip, Group, Paper, ScrollArea, Stack, Text } from "@mantine/core";
import { useEffect, useMemo, useRef, useState } from "react";
import type { Classe, Horizonte, SaidaDireta } from "../api/types";
import { formatPrazo, formatValor, idadeRelativa } from "../util/formato";
import { corClasse } from "../util/rotulos";
import { useUI } from "../store/ui";
import { useTriagem } from "../triagem/useTriagem";
import { useTriagemKeys } from "../triagem/useTriagemKeys";
import { DrawerDetalhe } from "./DrawerDetalhe";
import { PageHeader } from "./PageHeader";

const LIMITE_IMPRODUTIVA_MS = 60_000;

const ROTULO: Record<SaidaDireta, string> = {
  resolver: "resolvido",
  reservar: "reservado",
  repousar: "adormecido",
  descartar: "descartado",
};

// Cor do acento por classe (barra à esquerda), na linguagem visual do protótipo.
const COR_CLASSE: Record<Classe, string> = {
  DECISAO: "var(--mantine-color-blue-6)",
  BLOQUEIO: "var(--mantine-color-red-6)",
  ESTEIRA: "var(--mantine-color-grape-6)",
};
const ROTULO_CLASSE: Record<Classe, string> = {
  DECISAO: "decisão",
  BLOQUEIO: "bloqueio",
  ESTEIRA: "esteira",
};

const HORIZONTES: Array<[string, string]> = [
  ["todos", "Todos"],
  ["HOJE", "Hoje"],
  ["SEMANA", "Semana"],
  ["TRIMESTRE", "Trimestre"],
];
const CLASSES: Array<[string, string]> = [
  ["todos", "Todas"],
  ["DECISAO", "Decisão"],
  ["BLOQUEIO", "Bloqueio"],
  ["ESTEIRA", "Esteira"],
];

export function EntradaPage() {
  const { itens, pendentes, aplicar, aplicarLote, desfazer } = useTriagem();
  const { cursor, selecao, setCursor } = useUI();

  const [fHorizonte, setFHorizonte] = useState<string>("todos");
  const [fClasse, setFClasse] = useState<string>("todos");
  const itensFiltrados = useMemo(
    () =>
      itens.filter(
        (p) =>
          (fHorizonte === "todos" || p.horizonte === (fHorizonte as Horizonte)) &&
          (fClasse === "todos" || p.classe === (fClasse as Classe)),
      ),
    [itens, fHorizonte, fClasse],
  );

  // Mantém o cursor dentro dos limites quando o filtro encolhe a lista.
  useEffect(() => {
    if (cursor > itensFiltrados.length - 1) {
      setCursor(Math.max(0, itensFiltrados.length - 1));
    }
  }, [itensFiltrados.length, cursor, setCursor]);

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

  useTriagemKeys({ itens: itensFiltrados, aplicar: aplicarC, aplicarLote: aplicarLoteC });

  return (
    <Box style={{ display: "flex", flexDirection: "column", height: "calc(100vh - 96px)" }}>
      <PageHeader titulo="Entrada" sub="A fila a esvaziar — uma pergunta: isso precisa mesmo de você?" />
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
              <Button size="xs" variant="subtle" color="gray" onClick={() => { aplicarLoteC([...selecao], "descartar"); useUI.getState().limparSelecao(); }}>
                Descartar (x)
              </Button>
            </Group>
          </Group>
        </Paper>
      )}

      <Group gap={6} mb="xs" data-testid="filtros">
        <Chip.Group multiple={false} value={fHorizonte} onChange={(v) => setFHorizonte(v as string)}>
          {HORIZONTES.map(([v, label]) => (
            <Chip key={v} value={v} size="xs" variant="light">
              {label}
            </Chip>
          ))}
        </Chip.Group>
        <span style={{ width: 1, height: 18, background: "var(--mantine-color-gray-3)" }} />
        <Chip.Group multiple={false} value={fClasse} onChange={(v) => setFClasse(v as string)}>
          {CLASSES.map(([v, label]) => (
            <Chip key={v} value={v} size="xs" variant="light">
              {label}
            </Chip>
          ))}
        </Chip.Group>
      </Group>

      <Paper p={0} style={{ flex: 1, minHeight: 0, overflow: "hidden" }}>
        <ScrollArea h="100%" type="auto" role="list" aria-label="entrada">
        {itensFiltrados.map((p, i) => (
          <div
            key={p.id}
            role="listitem"
            data-testid={`item-${p.id}`}
            data-cursor={i === cursor ? "true" : undefined}
            aria-selected={selecao.has(p.id)}
            onClick={() => setCursor(i)}
            style={{
              display: "flex",
              gap: 12,
              alignItems: "flex-start",
              padding: "11px 14px",
              cursor: "pointer",
              borderLeft: `3px solid ${COR_CLASSE[p.classe]}`,
              borderBottom: i < itensFiltrados.length - 1 ? "1px solid var(--linha)" : undefined,
              background: i === cursor ? "var(--mantine-color-indigo-0)" : selecao.has(p.id) ? "var(--papel-2)" : "#fff",
            }}
          >
            <div style={{ minWidth: 0, flex: 1 }}>
              <Text fw={500} lineClamp={1}>{p.titulo}</Text>
              {p.oQueTrava && (
                <Text size="sm" c="dimmed" lineClamp={1}>
                  {p.oQueTrava}
                </Text>
              )}
              <Group gap={8} mt={5} wrap="wrap">
                <Badge size="xs" variant="light" color={corClasse(p.classe)}>
                  {ROTULO_CLASSE[p.classe]}
                </Badge>
                {p.quemEspera && (
                  <Text size="xs" c="dimmed">espera: {p.quemEspera}</Text>
                )}
                {p.temperatura > 0 && (
                  <Badge size="xs" color="orange" variant="light">
                    {p.temperatura} {p.temperatura === 1 ? "cobrança" : "cobranças"}
                  </Badge>
                )}
                {p.baixaConfianca && (
                  <Badge size="xs" color="gray" variant="outline">rever</Badge>
                )}
                {selecao.has(p.id) && (
                  <Badge size="xs" data-testid="selecionado">no lote</Badge>
                )}
                <Anchor href={`/bloco/${p.id}`} size="xs" onClick={(e) => e.stopPropagation()}>
                  abrir bloco
                </Anchor>
              </Group>
            </div>
            <Stack gap={1} align="flex-end" style={{ flexShrink: 0 }}>
              {formatValor(p.valorEmJogo) && (
                <Text fw={700} size="sm">{formatValor(p.valorEmJogo)}</Text>
              )}
              {idadeRelativa(p.criadaEm) && (
                <Text size="xs" c="dimmed" ff="monospace">{idadeRelativa(p.criadaEm)}</Text>
              )}
              {formatPrazo(p.prazoImplicito) && (
                <Text size="xs" c="red.7" ff="monospace">prazo {formatPrazo(p.prazoImplicito)}</Text>
              )}
            </Stack>
          </div>
        ))}
        {itensFiltrados.length === 0 && (
          <Text c="dimmed" ta="center" py={48}>
            {itens.length === 0
              ? "Entrada vazia. Nada depende de você agora."
              : "Nada neste filtro."}
          </Text>
        )}
        </ScrollArea>
      </Paper>

      <DrawerDetalhe />
    </Box>
  );
}
