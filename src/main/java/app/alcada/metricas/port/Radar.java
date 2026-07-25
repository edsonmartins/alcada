package app.alcada.metricas.port;

import app.alcada.plataforma.multitenancy.port.OrgId;

/** Diagnóstico de gargalo/encolhimento de uma organização (leitura pura). */
public interface Radar {

    RadarDados calcular(OrgId org);
}
