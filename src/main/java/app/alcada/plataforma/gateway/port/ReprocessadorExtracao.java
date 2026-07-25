package app.alcada.plataforma.gateway.port;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Reexecuta uma extração que ficou pendente por indisponibilidade do gateway.
 * Implementado por {@code captura} (pacote 001), que sabe buscar o bruto no
 * Linktor, minimizar de novo e chamar o gateway. O worker de reprocesso do 018
 * apenas invoca esta porta.
 */
public interface ReprocessadorExtracao {

    void reprocessar(OrgId org, UUID refMensagemId);
}
