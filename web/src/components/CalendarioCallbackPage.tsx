import { Alert, Button, Stack, Text } from "@mantine/core";
import { useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { conectarCalendario, stateConfere } from "../api/calendario";
import { ProblemaError } from "../api/types";
import { PageHeader } from "./PageHeader";

type Situacao = "trocando" | "pronto" | "erro";

/**
 * Retorno do consentimento (RFC-0009): o provedor devolve o gestor aqui com um
 * `code`. Conferimos o `state` — se não for o que este navegador começou, não
 * trocamos nada — e mandamos o código ao servidor, que faz a troca por tokens.
 */
export function CalendarioCallbackPage() {
  const navigate = useNavigate();
  const [situacao, setSituacao] = useState<Situacao>("trocando");
  const [erro, setErro] = useState<string>("");

  useEffect(() => {
    const q = new URLSearchParams(window.location.search);
    const recusa = q.get("error");
    const codigo = q.get("code");
    if (recusa || !codigo) {
      setSituacao("erro");
      setErro(recusa === "access_denied"
        ? "Você não autorizou o acesso à agenda."
        : "O provedor não devolveu o código de autorização.");
      return;
    }
    if (!stateConfere(q.get("state"))) {
      setSituacao("erro");
      setErro("O retorno não confere com o pedido feito neste navegador. Tente conectar de novo.");
      return;
    }
    conectarCalendario(codigo)
      .then(() => setSituacao("pronto"))
      .catch((e) => {
        setSituacao("erro");
        setErro(e instanceof ProblemaError ? e.message : "Não consegui concluir a conexão.");
      });
  }, []);

  return (
    <Stack maw={560} mx="auto" mt={64}>
      <PageHeader titulo="Calendário" sub="Conectando sua agenda ao Alçada." />
      {situacao === "trocando" && <Text size="sm">Concluindo a conexão…</Text>}
      {situacao === "pronto" && (
        <Alert color="teal" variant="light">
          Agenda conectada. Os compromissos que você marcar ao resolver um item entram aqui.
        </Alert>
      )}
      {situacao === "erro" && <Alert color="red" variant="light">{erro}</Alert>}
      <Button variant="light" style={{ alignSelf: "flex-start" }}
        onClick={() => navigate({ to: "/canais" })}>
        Voltar para canais e contatos
      </Button>
    </Stack>
  );
}
