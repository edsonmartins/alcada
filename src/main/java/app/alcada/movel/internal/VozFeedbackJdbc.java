package app.alcada.movel.internal;

import app.alcada.movel.port.VozFeedback;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Persiste os desfechos de confirmação em {@code voz_confirmacao} e computa a taxa
 * de correção por org (INV-15). Sem pessoa_id — métrica agregada (ADR-0017).
 */
@ApplicationScoped
public class VozFeedbackJdbc implements VozFeedback {

    private final EntityManager em;

    public VozFeedbackJdbc(EntityManager em) {
        this.em = em;
    }

    @Override
    @Transactional
    public void registrar(OrgId org, boolean confirmado) {
        em.createNativeQuery(
                "INSERT INTO voz_confirmacao (org_id, resultado) VALUES (?, ?)")
                .setParameter(1, org.valor())
                .setParameter(2, confirmado ? "CONFIRMADO" : "CORRIGIDO")
                .executeUpdate();
    }

    @Override
    @Transactional
    public TaxaCorrecao taxa(OrgId org, int dias) {
        int janela = dias <= 0 ? 30 : dias;
        Object[] r = (Object[]) em.createNativeQuery("""
                SELECT
                  count(*) FILTER (WHERE resultado = 'CONFIRMADO'),
                  count(*) FILTER (WHERE resultado = 'CORRIGIDO')
                FROM voz_confirmacao
                WHERE org_id = ? AND ocorrido_em >= now() - (? * interval '1 day')
                """)
                .setParameter(1, org.valor())
                .setParameter(2, janela)
                .getSingleResult();
        long confirmados = ((Number) r[0]).longValue();
        long corrigidos = ((Number) r[1]).longValue();
        long total = confirmados + corrigidos;
        double taxa = total == 0 ? 0.0 : (double) corrigidos / total;
        return new TaxaCorrecao(confirmados, corrigidos, taxa);
    }
}
