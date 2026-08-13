package app.alcada.autonomia.port;

import java.time.Duration;
import java.time.OffsetDateTime;
import app.alcada.plataforma.multitenancy.port.OrgId;

/** Aritmética de tempo útil no calendário local da organização (ADR-0031). */
public interface CalendarioComercial {
    Duration tempoUtilEntre(OrgId org, OffsetDateTime inicio, OffsetDateTime fim);
    OffsetDateTime adicionarTempoUtil(OrgId org, OffsetDateTime inicio, Duration duracao);
    default OffsetDateTime instanteNaFracao(OrgId org, OffsetDateTime inicio, OffsetDateTime fim, int percentual) {
        Duration total=tempoUtilEntre(org,inicio,fim);
        return adicionarTempoUtil(org,inicio,Duration.ofSeconds(total.toSeconds()*percentual/100));
    }
    OffsetDateTime proximaAbertura(OrgId org, OffsetDateTime instante);
}
