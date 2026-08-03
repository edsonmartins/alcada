import { Alert, Badge, Button, Group, Paper, Select, SimpleGrid, Stack, Table, Text, TextInput, Title } from "@mantine/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  criarContato,
  editarContato,
  getContatos,
  type CanalContato,
  type ContatoExterno,
  type DadosContato,
} from "../api/contatos";
import { definirCanal, getFontes, type Fonte } from "../api/fontes";
import { ProblemaError } from "../api/types";
import { PageHeader, Vazio } from "./PageHeader";

const CONTATOS = ["contatos"] as const;
const FONTES = ["fontes"] as const;

const CANAIS: { value: CanalContato; label: string }[] = [
  { value: "WHATSAPP", label: "WhatsApp" },
  { value: "EMAIL", label: "E-mail" },
];

export function rotuloCanal(canal: string | null | undefined): string {
  return canal === "EMAIL" ? "E-mail" : "WhatsApp";
}

export function CanaisPage() {
  return (
    <Stack>
      <PageHeader
        titulo="Canais e contatos"
        sub="Por onde o Alçada avisa quem recebe um repasse. Quem não é usuário responde pelo canal — você não precisa avisar por fora."
      />
      <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="md" style={{ alignItems: "start" }}>
        <SecaoContatos />
        <SecaoCanalSaida />
      </SimpleGrid>
    </Stack>
  );
}

// ---- contatos externos -----------------------------------------------------

function SecaoContatos() {
  const { data: contatos } = useQuery({ queryKey: CONTATOS, queryFn: getContatos });
  const [editando, setEditando] = useState<ContatoExterno | null>(null);
  const qc = useQueryClient();
  const invalidar = () => {
    setEditando(null);
    qc.invalidateQueries({ queryKey: CONTATOS });
  };

  return (
    <Stack gap="xs">
      <Title order={6}>Contatos de repasse</Title>
      <Text size="xs" c="dimmed">
        Registrar um contato é escape, não cadastro (INV-02): serve para delegar a quem está fora do
        Alçada. Telefone e e-mail são dados pessoais — só o necessário para avisar.
      </Text>

      {contatos && contatos.length > 0 ? (
        <Paper withBorder p="xs">
          <Table verticalSpacing="xs" horizontalSpacing="sm">
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Nome</Table.Th>
                <Table.Th>Canal</Table.Th>
                <Table.Th>Endereço</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {contatos.map((c) => (
                <Table.Tr key={c.id} data-testid={`contato-${c.id}`}>
                  <Table.Td>{c.nome}</Table.Td>
                  <Table.Td>
                    <Badge size="xs" variant="light">{rotuloCanal(c.canal)}</Badge>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm" c="dimmed">{c.endereco}</Text>
                  </Table.Td>
                  <Table.Td style={{ textAlign: "right" }}>
                    <Button size="compact-xs" variant="subtle"
                      onClick={() => setEditando(c)}
                      aria-label={`Editar ${c.nome}`}>
                      Editar
                    </Button>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Paper>
      ) : (
        contatos && <Vazio>Nenhum contato externo. Todo repasse hoje é para dentro do Alçada.</Vazio>
      )}

      <FormularioContato
        key={editando?.id ?? "novo"}
        contato={editando}
        aoSalvar={invalidar}
        aoCancelar={() => setEditando(null)}
      />
    </Stack>
  );
}

function FormularioContato({ contato, aoSalvar, aoCancelar }: {
  contato: ContatoExterno | null;
  aoSalvar: () => void;
  aoCancelar: () => void;
}) {
  const [nome, setNome] = useState(contato?.nome ?? "");
  const [canal, setCanal] = useState<CanalContato>(contato?.canal ?? "WHATSAPP");
  const [endereco, setEndereco] = useState(contato?.endereco ?? "");

  const salvar = useMutation({
    mutationFn: (dados: DadosContato) =>
      contato ? editarContato(contato.id, dados) : criarContato(dados).then(() => undefined),
    onSuccess: () => {
      if (!contato) {
        setNome("");
        setEndereco("");
      }
      aoSalvar();
    },
  });

  const valido = nome.trim().length > 0 && endereco.trim().length > 0;
  const erro = salvar.error;

  return (
    <Paper withBorder p="md">
      <Title order={6} mb="xs">{contato ? `Editar ${contato.nome}` : "Novo contato"}</Title>
      <Group gap="xs" align="flex-end" wrap="wrap">
        <TextInput label="Nome" size="xs" value={nome} style={{ flex: "1 1 140px" }}
          onChange={(e) => setNome(e.currentTarget.value)} />
        <Select label="Canal" size="xs" w={110} data={CANAIS} value={canal} allowDeselect={false}
          onChange={(v) => setCanal((v as CanalContato) ?? "WHATSAPP")} />
        <TextInput
          label={canal === "EMAIL" ? "E-mail" : "Telefone"}
          placeholder={canal === "EMAIL" ? "nome@empresa.com.br" : "+5521999990000"}
          size="xs" value={endereco} style={{ flex: "1 1 180px" }}
          onChange={(e) => setEndereco(e.currentTarget.value)} />
        <Button size="xs" disabled={!valido || salvar.isPending}
          onClick={() => salvar.mutate({ nome: nome.trim(), canal, endereco: endereco.trim() })}>
          {contato ? "Salvar" : "Registrar"}
        </Button>
        {contato && (
          <Button size="xs" variant="default" onClick={aoCancelar}>Cancelar</Button>
        )}
      </Group>
      {erro && (
        <Alert color="red" mt="sm" variant="light">
          {erro instanceof ProblemaError ? erro.message : "Não consegui salvar o contato."}
        </Alert>
      )}
    </Paper>
  );
}

// ---- canal de saída --------------------------------------------------------

function SecaoCanalSaida() {
  const { data: fontes } = useQuery({ queryKey: FONTES, queryFn: getFontes });
  const whatsapp = (fontes ?? []).filter((f) => f.tipo === "WHATSAPP");
  // O despachante usa a primeira fonte WHATSAPP ativa com canal configurado.
  const emUso = whatsapp.find((f) => f.ativa && f.linktorChannelId);

  return (
    <Stack gap="xs">
      <Title order={6}>Canal de saída (WhatsApp)</Title>
      <Text size="xs" c="dimmed">
        O aviso de repasse sai pelo canal do Linktor da primeira fonte WhatsApp ativa. Sem canal
        configurado, o aviso fica na fila até você configurar — nada se perde (INV-13).
      </Text>

      {emUso ? (
        <Alert color="teal" variant="light">
          Avisando por <b>{emUso.identificador}</b> (canal <code>{emUso.linktorChannelId}</code>).
        </Alert>
      ) : (
        <Alert color="yellow" variant="light">
          Nenhuma fonte WhatsApp ativa com canal configurado — os avisos de repasse por WhatsApp
          ficam represados.
        </Alert>
      )}

      {whatsapp.length === 0 && fontes && (
        <Vazio>Nenhuma fonte WhatsApp cadastrada neste tenant.</Vazio>
      )}
      {whatsapp.map((f) => (
        <LinhaFonte key={f.id} f={f} />
      ))}

      <Text size="xs" c="dimmed" mt="xs">
        O aviso por e-mail usa o remetente configurado no servidor (SMTP), igual para todos os
        tenants — não se ajusta por aqui.
      </Text>
    </Stack>
  );
}

function LinhaFonte({ f }: { f: Fonte }) {
  const [canal, setCanal] = useState(f.linktorChannelId ?? "");
  const qc = useQueryClient();
  const salvar = useMutation({
    mutationFn: () => definirCanal(f.id, canal.trim()),
    onSuccess: () => qc.invalidateQueries({ queryKey: FONTES }),
  });

  return (
    <Paper withBorder p="sm" data-testid={`fonte-${f.id}`}>
      <Group justify="space-between" wrap="nowrap" mb={6}>
        <Text size="sm" fw={500}>{f.identificador}</Text>
        <Badge size="xs" variant="light" color={f.ativa ? "teal" : "gray"}>
          {f.ativa ? "ativa" : "inativa"}
        </Badge>
      </Group>
      <Group gap="xs" align="flex-end" wrap="nowrap">
        <TextInput label="Canal do Linktor" size="xs" style={{ flex: 1 }} value={canal}
          placeholder="channel id" aria-label={`Canal do Linktor de ${f.identificador}`}
          onChange={(e) => setCanal(e.currentTarget.value)} />
        <Button size="xs" onClick={() => salvar.mutate()} disabled={salvar.isPending}>
          Salvar canal
        </Button>
      </Group>
      {salvar.error && (
        <Alert color="red" mt="xs" variant="light">Não consegui salvar o canal.</Alert>
      )}
    </Paper>
  );
}
