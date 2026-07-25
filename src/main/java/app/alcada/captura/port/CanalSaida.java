package app.alcada.captura.port;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Fechamento do laço no canal de origem: responde com id e estado (ADR-0013).
 * A entrega efetiva é assíncrona, via outbox → Linktor. O {@code pendenciaId}
 * viaja no efeito para que a trilha de comunicação (006) seja rastreável.
 */
public interface CanalSaida {

    void responder(OrgId org, UUID pendenciaId, EnviarMensagem mensagem);
}
