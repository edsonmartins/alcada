package app.alcada.consulta.port;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Consulta em linguagem natural sobre a fila (RFC-0004 §3). Não é RAG livre: a
 * pergunta é traduzida para uma consulta de uma whitelist fechada e executada de
 * forma determinística, escopada por organização (INV-15). O modelo, quando
 * habilitado, apenas escolhe o template — nunca gera SQL nem inventa dados
 * (INV-10).
 */
public interface Consulta {

    ResultadoConsulta consultar(OrgId org, String pergunta);
}
