package app.alcada.plataforma.outbox.internal;

import app.alcada.plataforma.outbox.port.MensagemOutbox;
import app.alcada.plataforma.outbox.port.Outbox;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/**
 * Publicação transacional: um INSERT no outbox que vive na transação do
 * chamador. Se a transição de estado reverte, o efeito externo nunca existe.
 * Idempotência por chave única — inserir a mesma chave duas vezes é no-op.
 */
@ApplicationScoped
public class OutboxTransacional implements Outbox {

    private static final String INSERT = """
            INSERT INTO outbox (org_id, tipo, payload, idempotency_key)
            VALUES (?, ?, cast(? as jsonb), ?)
            ON CONFLICT (idempotency_key) DO NOTHING
            """;

    private final EntityManager em;

    public OutboxTransacional(EntityManager em) {
        this.em = em;
    }

    @Override
    public void publicar(MensagemOutbox m) {
        em.createNativeQuery(INSERT)
                .setParameter(1, m.org().valor())
                .setParameter(2, m.tipo())
                .setParameter(3, m.payloadJson())
                .setParameter(4, m.idempotencyKey())
                .executeUpdate();
    }
}
