package app.alcada.plataforma.scheduler.port;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Executa jobs de um {@link #tipo()}. Implementações concretas chegam nos
 * pacotes de domínio (ex.: motor de autonomia). O worker roteia por tipo.
 *
 * <p>A execução deve ser idempotente: um job pode ser reprocessado após
 * reinício antes de ser marcado concluído.
 */
public interface ExecutorJob {

    String tipo();

    void executar(OrgId org, String chave, String payloadJson);
}
