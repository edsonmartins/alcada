package app.alcada.assistente.port;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/** Índice do dossiê: (re)indexação e perguntas com recuperação híbrida (BM25 + embeddings). */
public interface Dossie {

    /** (Re)indexa as passagens de uma pendência (idempotente). */
    void indexar(OrgId org, UUID pendenciaId);

    /** Recupera passagens da base do tenant e responde citando fonte. */
    RespostaDossie perguntar(OrgId org, UUID pendenciaId, String pergunta);
}
