import { Alert,Anchor,Badge,Button,Group,Paper,Stack,Text,Title } from "@mantine/core";
import { useMutation,useQuery,useQueryClient } from "@tanstack/react-query";
import { useParams } from "@tanstack/react-router";
import { decidirRetorno,detalheItem } from "../api/itens";

export function ItemDetalhePage(){
  const {id}=useParams({from:"/itens/$id"});
  const retorno=new URLSearchParams(window.location.search).get("retorno");
  const qc=useQueryClient();
  const {data,isLoading}=useQuery({queryKey:["item",id],queryFn:()=>detalheItem(id)});
  const decisao=useMutation({mutationFn:(valor:"APLICAR"|"REJEITAR")=>decidirRetorno(retorno!,valor,crypto.randomUUID()),onSuccess:()=>qc.invalidateQueries({queryKey:["item",id]})});
  if(isLoading)return <Text c="dimmed">Consultando história…</Text>;
  if(!data)return <Text>Item não encontrado.</Text>;
  const i=data.item;
  return <Stack>
    <Anchor href="/itens">← Voltar à consulta</Anchor>
    <div><Title order={2}>{i.titulo}</Title><Group><Badge>{i.classe}</Badge><Badge variant="outline">{i.status}</Badge>{i.nivel&&<Badge variant="light">{i.nivel}</Badge>}</Group></div>
    {retorno&&<Alert title="Retorno recebido" color={decisao.isSuccess?"green":"blue"}>{decisao.isSuccess?<Text>Retorno avaliado. Agora escolha a Saída adequada para a Pendência.</Text>:<Stack gap="xs"><Text>Este retorno precisa ser considerado antes do próximo encaminhamento.</Text><Group><Button loading={decisao.isPending} onClick={()=>decisao.mutate("APLICAR")}>Considerar retorno</Button><Button variant="outline" color="red" loading={decisao.isPending} onClick={()=>decisao.mutate("REJEITAR")}>Rejeitar retorno</Button></Group></Stack>}</Alert>}
    <Paper withBorder p="md"><Text size="sm">{[i.executor,i.origem,i.quemEspera].filter(Boolean).join(" · ")||"Sem contraparte reconhecida"}</Text><Group mt="xs">{i.links.filter(l=>l.tipo!=="TRILHA").map(l=><Anchor href={l.href} key={l.tipo}>{l.tipo.toLowerCase()}</Anchor>)}</Group></Paper>
    <Title order={4}>História</Title>{data.trilha.map((e,n)=><Paper key={n} withBorder p="sm"><Text fw={600}>{e.tipo}</Text><Text size="xs" c="dimmed">{new Date(e.ocorridoEm).toLocaleString("pt-BR")} · {e.ator}</Text></Paper>)}
  </Stack>;
}
