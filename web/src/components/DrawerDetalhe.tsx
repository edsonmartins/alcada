import { Button, Drawer, Group, Select, Stack, Text, TextInput } from "@mantine/core";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import { adiar, pedirInformacao, repassar } from "../api/pendencias";
import { getDestinos } from "../api/destinos";
import type { Pendencia } from "../api/types";
import { ENTRADA_KEY } from "../triagem/useTriagem";
import { useUI } from "../store/ui";

const repassarSchema = z.object({
  destinoChave: z.string().min(1, "escolha uma pessoa ou contato"),
  nivel: z.enum(["N1", "N2", "N3"]),
  prazo: z.string().min(1, "informe o prazo"),
});
type RepassarForm = z.infer<typeof repassarSchema>;

const adiarSchema = z.object({
  voltaEm: z.string().min(1, "data obrigatória (\"depois\" não é aceito)"),
  oQueFalta: z.enum(["NADA", "INSUMO", "TERCEIRO"]),
});
type AdiarForm = z.infer<typeof adiarSchema>;
const pedidoSchema = z.object({
  contatoId: z.string().min(1, "escolha quem possui o insumo"),
  pergunta: z.string().min(3, "escreva uma pergunta objetiva").max(1000),
  prazo: z.string().min(1, "informe quando cobrar de volta"),
});
type PedidoForm = z.infer<typeof pedidoSchema>;

export function DrawerDetalhe() {
  const { drawerId, form, fecharDrawer } = useUI();
  const qc = useQueryClient();
  const aberto = drawerId !== null;

  function aoConcluir() {
    void qc.invalidateQueries({ queryKey: ENTRADA_KEY });
    fecharDrawer();
  }

  return (
    <Drawer opened={aberto} onClose={fecharDrawer} position="right" title="Pendência" padding="md">
      {drawerId && form === "repassar" && (
        <RepassarForm pendenciaId={drawerId} aoConcluir={aoConcluir} />
      )}
      {drawerId && form === "adiar" && <AdiarForm pendenciaId={drawerId} aoConcluir={aoConcluir} />}
      {drawerId && form === "pedido_informacao" && <PedidoInformacaoForm pendenciaId={drawerId} aoConcluir={aoConcluir} />}
      {drawerId && !form && (
        <Text c="dimmed" size="sm">
          Use <b>1</b> resolver · <b>2</b> repassar · <b>3</b> reservar · <b>4</b> repousar · <b>a</b> adiar.
        </Text>
      )}
    </Drawer>
  );
}

function RepassarForm({ pendenciaId, aoConcluir }: { pendenciaId: string; aoConcluir: () => void }) {
  const qc = useQueryClient();
  const classe = qc.getQueryData<Pendencia[]>(ENTRADA_KEY)?.find((p) => p.id === pendenciaId)?.classe;
  const [busca, setBusca] = useState("");
  const [novo, setNovo] = useState(false);
  const [novoNome, setNovoNome] = useState("");
  const [novoCanal, setNovoCanal] = useState<"WHATSAPP" | "EMAIL">("WHATSAPP");
  const [novoEndereco, setNovoEndereco] = useState("");
  const { data: destinos = [], isLoading, isError } = useQuery({
    queryKey: ["destinos-repasse", busca, classe],
    queryFn: () => getDestinos(busca, classe),
  });
  const { control, handleSubmit, register, formState, setValue } = useForm<RepassarForm>({
    resolver: zodResolver(repassarSchema),
    defaultValues: { nivel: "N2", destinoChave: "", prazo: "" },
  });

  async function onSubmit(v: RepassarForm) {
    if (novo) {
      if (!novoNome.trim() || !novoEndereco.trim()) return;
      await repassar(pendenciaId, { destino: { tipo: "EXTERNO_NOVO", nome: novoNome, canal: novoCanal, endereco: novoEndereco }, nivel: v.nivel, prazo: new Date(v.prazo).toISOString() });
      aoConcluir();
      return;
    }
    const escolhido = destinos.find((d) => `${d.tipo}:${d.id}` === v.destinoChave);
    if (!escolhido) return;
    const destino = escolhido.tipo === "INTERNO"
      ? { tipo: "INTERNO" as const, pessoaId: escolhido.id }
      : { tipo: "EXTERNO" as const, contatoId: escolhido.id };
    await repassar(pendenciaId, { destino, nivel: v.nivel, prazo: new Date(v.prazo).toISOString() });
    aoConcluir();
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} aria-label="repassar">
      <Stack>
        <Controller name="destinoChave" control={control} render={({ field }) => (
          <Select
            label="Responsável"
            searchable
            searchValue={busca}
            onSearchChange={setBusca}
            placeholder={isLoading ? "Buscando…" : "Nome da pessoa ou contato"}
            nothingFoundMessage={isError ? "Não foi possível buscar" : "Nenhum destino encontrado"}
            data={destinos.map((d) => ({ value: `${d.tipo}:${d.id}`, label: `${d.nome} — ${d.detalhe}` }))}
            value={field.value}
            onChange={(v) => {
              field.onChange(v ?? "");
              const d = destinos.find((x) => `${x.tipo}:${x.id}` === v);
              if (d?.nivelSugerido) setValue("nivel", d.nivelSugerido);
              if (d?.prazoSugerido) setValue("prazo", d.prazoSugerido.slice(0, 16));
            }}
            error={formState.errors.destinoChave?.message}
          />
        )} />
        <Text size="xs" c="dimmed">Equipe ou contato externo; você confirma o contrato antes do envio.</Text>
        <Button type="button" size="compact-xs" variant="subtle" px={0} onClick={() => {
          setNovo((v) => !v); setValue("destinoChave", novo ? "" : "NOVO");
        }}>{novo ? "Escolher contato existente" : "Adicionar contato durante o repasse"}</Button>
        {novo && <Stack gap="xs">
          <TextInput label="Nome do contato" value={novoNome} onChange={(e) => setNovoNome(e.currentTarget.value)} required />
          <Select label="Canal" data={["WHATSAPP", "EMAIL"]} value={novoCanal} onChange={(v) => setNovoCanal(v as "WHATSAPP" | "EMAIL")} allowDeselect={false} />
          <TextInput label={novoCanal === "EMAIL" ? "E-mail" : "Telefone"} value={novoEndereco} onChange={(e) => setNovoEndereco(e.currentTarget.value)} required />
        </Stack>}
        <Controller
          name="nivel"
          control={control}
          render={({ field }) => (
            <Select label="Nível" data={["N1", "N2", "N3"]} value={field.value} onChange={(v) => field.onChange(v)} />
          )}
        />
        <TextInput type="datetime-local" label="Prazo" error={formState.errors.prazo?.message} {...register("prazo")} />
        <Group justify="flex-end">
          <Button type="submit">Repassar</Button>
        </Group>
      </Stack>
    </form>
  );
}

function AdiarForm({ pendenciaId, aoConcluir }: { pendenciaId: string; aoConcluir: () => void }) {
  const abrirDrawer = useUI((s) => s.abrirDrawer);
  const { control, handleSubmit, register, formState } = useForm<AdiarForm>({
    resolver: zodResolver(adiarSchema),
    defaultValues: { voltaEm: "", oQueFalta: "NADA" },
  });

  async function onSubmit(v: AdiarForm) {
    const r = await adiar(pendenciaId, { voltaEm: new Date(v.voltaEm).toISOString(), oQueFalta: v.oQueFalta });
    if (r.oferta === "cobrar_insumo" || (r.oferta === "repassar" && v.oQueFalta === "TERCEIRO")) {
      abrirDrawer(pendenciaId, "pedido_informacao");
    } else aoConcluir();
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} aria-label="adiar">
      <Stack>
        <Text size="sm" c="dimmed">
          Adiar é ausência de decisão — precisa de data e do que falta.
        </Text>
        <TextInput type="date" label="Volta em" error={formState.errors.voltaEm?.message} {...register("voltaEm")} />
        <Controller
          name="oQueFalta"
          control={control}
          render={({ field }) => (
            <Select
              label="O que falta"
              data={[
                { value: "NADA", label: "Nada — está evitado" },
                { value: "INSUMO", label: "Insumo" },
                { value: "TERCEIRO", label: "Terceiro" },
              ]}
              value={field.value}
              onChange={(v) => field.onChange(v)}
            />
          )}
        />
        <Group justify="flex-end">
          <Button type="submit" variant="default">
            Adiar
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

function PedidoInformacaoForm({ pendenciaId, aoConcluir }: { pendenciaId: string; aoConcluir: () => void }) {
  const qc = useQueryClient();
  const pendencia = qc.getQueryData<Pendencia[]>(ENTRADA_KEY)?.find((p) => p.id === pendenciaId);
  const [busca, setBusca] = useState("");
  const { data: destinos = [], isLoading } = useQuery({
    queryKey: ["destinos-repasse", busca, pendencia?.classe],
    queryFn: () => getDestinos(busca, pendencia?.classe),
  });
  const externos = destinos.filter((d) => d.tipo === "EXTERNO" && d.canal === "WHATSAPP");
  const { control, register, handleSubmit, formState } = useForm<PedidoForm>({
    resolver: zodResolver(pedidoSchema),
    defaultValues: {
      contatoId: "",
      pergunta: pendencia ? `Pode informar o que falta para: ${pendencia.titulo}?` : "Pode informar o insumo necessário?",
      prazo: "",
    },
  });
  async function onSubmit(v: PedidoForm) {
    await pedirInformacao(pendenciaId, { ...v, prazo: new Date(v.prazo).toISOString() });
    aoConcluir();
  }
  return <form onSubmit={handleSubmit(onSubmit)} aria-label="pedido de informação">
    <Stack>
      <Text size="sm" c="dimmed">A Pendência repousa enquanto esta pessoa tem a próxima ação. Revise e confirme a pergunta.</Text>
      <Controller name="contatoId" control={control} render={({ field }) => <Select
        label="Quem possui o insumo" searchable searchValue={busca} onSearchChange={setBusca}
        placeholder={isLoading ? "Buscando…" : "Contato externo no WhatsApp"}
        data={externos.map((d) => ({ value: d.id, label: `${d.nome} — ${d.detalhe}` }))}
        value={field.value} onChange={(v) => field.onChange(v ?? "")}
        error={formState.errors.contatoId?.message}
      />} />
      <TextInput label="Pergunta objetiva" {...register("pergunta")} error={formState.errors.pergunta?.message} />
      <TextInput type="datetime-local" label="Aguardar até" {...register("prazo")} error={formState.errors.prazo?.message} />
      <Group justify="flex-end"><Button type="submit">Confirmar e pedir informação</Button></Group>
    </Stack>
  </form>;
}
