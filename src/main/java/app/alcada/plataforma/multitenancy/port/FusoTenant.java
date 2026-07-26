package app.alcada.plataforma.multitenancy.port;

import java.time.ZoneId;

/**
 * Fuso horário da organização (002). Usado para ancorar apresentação e cálculo
 * de "dia"/"semana" (radar, revisão, bloco, aprendizado) no fuso do tenant, em
 * vez de um fixo. Não altera instantes absolutos (prazos/janelas em timestamptz).
 */
public interface FusoTenant {

    /** Fuso da organização; default {@code America/Sao_Paulo} se ausente/inválido. */
    ZoneId fuso(OrgId org);
}
