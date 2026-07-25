package app.alcada.assistente.port;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/** Bloco de decisão: dossiê (leitura), redação (proposta) e decidir (código). */
public interface Bloco {

    BlocoDados montar(OrgId org, UUID pendenciaId);

    RascunhoResultado redigir(OrgId org, UUID pendenciaId, String opcao, String tom);

    /** Aplica a decisão: fecha a pendência e enfileira a comunicação. INV-10. */
    void decidir(OrgId org, UUID pendenciaId, String opcao, String texto, UUID por);
}
