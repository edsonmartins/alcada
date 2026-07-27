package app.alcada.plataforma.outbox.internal;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.port.MensagemOutbox;
import app.alcada.plataforma.outbox.port.Outbox;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/**
 * Publicação transacional: um INSERT no outbox que vive na transação do
 * chamador. Se a transição de estado reverte, o efeito externo nunca existe.
 * Idempotência por chave única — inserir a mesma chave duas vezes é no-op.
 *
 * <p>Se a thread está processando um comando de trajeto ({@link ContextoTrajeto}),
 * a linha nasce represada ({@code trajeto_id} preenchido) e o worker não a emite
 * até a liberação (023, INV-14).
 */
@ApplicationScoped
public class OutboxTransacional implements Outbox {

    private static final String INSERT = """
            INSERT INTO outbox (org_id, tipo, payload, idempotency_key, trajeto_id, pendencia_id)
            VALUES (?, ?, cast(? as jsonb), ?, ?, ?)
            ON CONFLICT (idempotency_key) DO NOTHING
            """;

    private static final String LIBERAR = """
            UPDATE outbox SET trajeto_id = NULL
            WHERE org_id = ? AND trajeto_id = ?
            """;

    private static final String DESCARTAR = """
            DELETE FROM outbox
            WHERE org_id = ? AND trajeto_id = ? AND pendencia_id = ?
            """;

    private final EntityManager em;
    private final ContextoTrajeto trajeto;

    public OutboxTransacional(EntityManager em, ContextoTrajeto trajeto) {
        this.em = em;
        this.trajeto = trajeto;
    }

    @Override
    public void publicar(MensagemOutbox m) {
        em.createNativeQuery(INSERT)
                .setParameter(1, m.org().valor())
                .setParameter(2, m.tipo())
                .setParameter(3, m.payloadJson())
                .setParameter(4, m.idempotencyKey())
                .setParameter(5, trajeto.atual().orElse(null))
                .setParameter(6, trajeto.pendencia().orElse(null))
                .executeUpdate();
    }

    @Override
    public void liberarTrajeto(OrgId org, UUID trajetoId) {
        em.createNativeQuery(LIBERAR)
                .setParameter(1, org.valor())
                .setParameter(2, trajetoId)
                .executeUpdate();
    }

    @Override
    public void descartarTrajeto(OrgId org, UUID trajetoId, UUID pendenciaId) {
        em.createNativeQuery(DESCARTAR)
                .setParameter(1, org.valor())
                .setParameter(2, trajetoId)
                .setParameter(3, pendenciaId)
                .executeUpdate();
    }
}
