package app.alcada.plataforma.trilha.port;

import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Leitura da trilha de uma pendência. Sempre escopada por organização (INV-15):
 * nunca retorna evento de outro tenant.
 */
public interface ConsultaTrilha {

    List<EventoRegistrado> daPendencia(OrgId org, UUID pendenciaId);
}
