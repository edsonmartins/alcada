package app.alcada.movel.port;

import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Sincronização de comandos do canal móvel (021). Lote idempotente: cada comando
 * executa no máximo uma vez por {@code (org, comandoId)} (INV-13). Mapeia intenção
 * → ação determinística existente (INV-10). Escopo por organização (INV-15).
 */
public interface ComandoMovel {

    List<ResultadoComando> sincronizar(OrgId org, UUID pessoa, List<Comando> comandos);
}
