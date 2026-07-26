package app.alcada.captura.port;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Escape manual de captura (ADR-0005): cria uma pendência direto em ENTRADA, sem
 * canal de origem. É métrica de falha da captura — usado pelo escape do web e
 * pela intenção REGISTRAR do canal móvel (021). {@code classe} nula assume DECISAO.
 */
public interface EscapeCaptura {

    /** @throws IllegalArgumentException título vazio ou classe inválida. */
    UUID registrar(OrgId org, String titulo, String quemEspera, String oQueTrava, String classe, UUID pessoa);
}
