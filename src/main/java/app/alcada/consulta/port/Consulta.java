package app.alcada.consulta.port;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Consulta em linguagem natural sobre a fila (RFC-0004 §3). Não é RAG livre: a
 * pergunta é traduzida para uma consulta de uma whitelist fechada e executada de
 * forma determinística, escopada por organização (INV-15). O modelo, quando
 * habilitado, apenas escolhe o template — nunca gera SQL nem inventa dados
 * (INV-10).
 */
public interface Consulta {

    /** Consulta org-escopada. Perguntas "o que EU decidi" precisam do gestor — use a sobrecarga. */
    default ResultadoConsulta consultar(OrgId org, String pergunta) {
        return consultar(org, null, pergunta);
    }

    /**
     * Consulta ciente do gestor (X-Pessoa-Id): a maioria dos templates é
     * org-escopada e ignora {@code gestor}; só "o que eu decidi" o usa para
     * filtrar a trilha pelo ator.
     */
    ResultadoConsulta consultar(OrgId org, UUID gestor, String pergunta);
}
