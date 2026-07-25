import { Button, Drawer, Group, Select, Stack, Text, TextInput } from "@mantine/core";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import { adiar, repassar } from "../api/pendencias";
import { ENTRADA_KEY } from "../triagem/useTriagem";
import { useUI } from "../store/ui";

const repassarSchema = z.object({
  donoId: z.string().uuid("informe um responsável"),
  nivel: z.enum(["N1", "N2", "N3"]),
  prazo: z.string().min(1, "informe o prazo"),
});
type RepassarForm = z.infer<typeof repassarSchema>;

const adiarSchema = z.object({
  voltaEm: z.string().min(1, "data obrigatória (\"depois\" não é aceito)"),
  oQueFalta: z.enum(["NADA", "INSUMO", "TERCEIRO"]),
});
type AdiarForm = z.infer<typeof adiarSchema>;

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
      {drawerId && !form && (
        <Text c="dimmed" size="sm">
          Use <b>1</b> resolver · <b>2</b> repassar · <b>3</b> reservar · <b>4</b> repousar · <b>a</b> adiar.
        </Text>
      )}
    </Drawer>
  );
}

function RepassarForm({ pendenciaId, aoConcluir }: { pendenciaId: string; aoConcluir: () => void }) {
  const { control, handleSubmit, register, formState } = useForm<RepassarForm>({
    resolver: zodResolver(repassarSchema),
    defaultValues: { nivel: "N2", donoId: "", prazo: "" },
  });

  async function onSubmit(v: RepassarForm) {
    await repassar(pendenciaId, { donoId: v.donoId, nivel: v.nivel, prazo: new Date(v.prazo).toISOString() });
    aoConcluir();
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} aria-label="repassar">
      <Stack>
        <TextInput label="Responsável" placeholder="id da pessoa" error={formState.errors.donoId?.message} {...register("donoId")} />
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
  const { control, handleSubmit, register, formState } = useForm<AdiarForm>({
    resolver: zodResolver(adiarSchema),
    defaultValues: { voltaEm: "", oQueFalta: "NADA" },
  });

  async function onSubmit(v: AdiarForm) {
    await adiar(pendenciaId, { voltaEm: new Date(v.voltaEm).toISOString(), oQueFalta: v.oQueFalta });
    aoConcluir();
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
