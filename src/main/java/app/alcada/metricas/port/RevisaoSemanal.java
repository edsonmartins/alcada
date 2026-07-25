package app.alcada.metricas.port;

import app.alcada.plataforma.multitenancy.port.OrgId;

/** Roteiro da revisão de sexta de uma organização (leitura pura). */
public interface RevisaoSemanal {

    RevisaoDados calcular(OrgId org);
}
