package app.alcada.movel.port;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Feedback de confirmação da voz (022): sinal de qualidade da interpretação. Quando
 * o assistente pede "confirma?" (ADR-0014), o gestor confirma ou corrige. A taxa de
 * correção é agregada por ORG (nunca por pessoa — ADR-0017).
 */
public interface VozFeedback {

    /** Registra um desfecho de confirmação. {@code confirmado=false} = correção. */
    void registrar(OrgId org, boolean confirmado);

    /** Taxa de correção da org na janela de {@code dias}. */
    TaxaCorrecao taxa(OrgId org, int dias);

    /** {@code taxa} = corrigidos / (confirmados + corrigidos); 0 quando não há dados. */
    record TaxaCorrecao(long confirmados, long corrigidos, double taxa) {
    }
}
