package app.alcada.metricas.port;

import java.util.OptionalInt;
import java.util.UUID;
import app.alcada.plataforma.multitenancy.port.OrgId;

/** Estimativa anti-vigilância: mediana pessoal exibida somente junto de ação. */
public interface EstimativaDespacho {
    OptionalInt minutos(OrgId org, UUID gestorId, int pendenciasDistintas);
}
