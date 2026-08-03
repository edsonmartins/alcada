import { fireEvent, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ContatoExterno } from "../api/contatos";
import type { Fonte } from "../api/fontes";

const CONTATOS: ContatoExterno[] = [
  { id: "c1", nome: "Clécia Souza", canal: "WHATSAPP", endereco: "+5521999990000" },
  { id: "c2", nome: "Paulo Cesar", canal: "EMAIL", endereco: "paulo@rioquality.com.br" },
];

const h = vi.hoisted(() => ({
  criarContato: vi.fn().mockResolvedValue({ id: "c3" }),
  editarContato: vi.fn().mockResolvedValue(undefined),
  definirCanal: vi.fn().mockResolvedValue(undefined),
  fontes: [] as Fonte[],
}));

vi.mock("../api/contatos", async () => {
  const real = await vi.importActual<typeof import("../api/contatos")>("../api/contatos");
  return {
    ...real,
    getContatos: () => Promise.resolve(CONTATOS),
    criarContato: h.criarContato,
    editarContato: h.editarContato,
  };
});

vi.mock("../api/fontes", async () => {
  const real = await vi.importActual<typeof import("../api/fontes")>("../api/fontes");
  return { ...real, getFontes: () => Promise.resolve(h.fontes), definirCanal: h.definirCanal };
});

import { CanaisPage } from "../components/CanaisPage";
import { renderComProviders } from "../test/util";

const FONTE_COM_CANAL: Fonte = {
  id: "f1",
  tipo: "WHATSAPP",
  identificador: "+5521988887777",
  ativa: true,
  linktorChannelId: "ch-rioquality",
};

describe("canais e contatos (RFC-0008 F1.5)", () => {
  // C22 — a tela lista os contatos com o canal de cada um
  it("lista os contatos externos com o canal", async () => {
    h.fontes = [FONTE_COM_CANAL];
    renderComProviders(<CanaisPage />);

    await screen.findByText("Clécia Souza");
    expect(screen.getByText("+5521999990000")).toBeInTheDocument();
    expect(screen.getByText("paulo@rioquality.com.br")).toBeInTheDocument();
    expect(screen.getAllByText("WhatsApp").length).toBeGreaterThan(0);
    expect(screen.getAllByText("E-mail").length).toBeGreaterThan(0);
  });

  // C22 — registrar um contato novo (escape, INV-02)
  it("registra um contato novo com nome, canal e endereço", async () => {
    h.fontes = [FONTE_COM_CANAL];
    renderComProviders(<CanaisPage />);
    await screen.findByText("Clécia Souza");

    fireEvent.change(screen.getByLabelText("Nome"), { target: { value: "Marcello Andrade" } });
    fireEvent.change(screen.getByLabelText("Telefone"), { target: { value: "+5521977776666" } });
    fireEvent.click(screen.getByRole("button", { name: "Registrar" }));

    await waitFor(() =>
      expect(h.criarContato).toHaveBeenCalledWith({
        nome: "Marcello Andrade",
        canal: "WHATSAPP",
        endereco: "+5521977776666",
      }),
    );
  });

  // C22 — o telefone muda; o contato é o mesmo
  it("edita o endereço de um contato existente", async () => {
    h.fontes = [FONTE_COM_CANAL];
    renderComProviders(<CanaisPage />);
    await screen.findByText("Clécia Souza");

    fireEvent.click(screen.getByRole("button", { name: "Editar Clécia Souza" }));
    fireEvent.change(await screen.findByLabelText("Telefone"), {
      target: { value: "+5521911112222" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() =>
      expect(h.editarContato).toHaveBeenCalledWith("c1", {
        nome: "Clécia Souza",
        canal: "WHATSAPP",
        endereco: "+5521911112222",
      }),
    );
  });

  // Canal de saída: a tela diz por onde o aviso sai hoje
  it("mostra a fonte que entrega o aviso e permite trocar o canal", async () => {
    h.fontes = [FONTE_COM_CANAL];
    renderComProviders(<CanaisPage />);

    await screen.findByText(/Avisando por/);
    expect(screen.getByText("ch-rioquality")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Canal do Linktor de +5521988887777"), {
      target: { value: "ch-novo" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Salvar canal" }));

    await waitFor(() => expect(h.definirCanal).toHaveBeenCalledWith("f1", "ch-novo"));
  });

  // Sem canal configurado o aviso fica represado — a tela avisa em vez de calar
  it("avisa quando nenhuma fonte WhatsApp tem canal configurado", async () => {
    h.fontes = [{ ...FONTE_COM_CANAL, linktorChannelId: null }];
    renderComProviders(<CanaisPage />);

    await screen.findByText(/ficam represados/);
  });
});
