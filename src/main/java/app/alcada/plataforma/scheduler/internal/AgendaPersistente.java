package app.alcada.plataforma.scheduler.internal;

import java.time.OffsetDateTime;

import app.alcada.plataforma.scheduler.port.Agenda;
import app.alcada.plataforma.scheduler.port.TarefaAgendada;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/**
 * Agenda persistente: um INSERT na tabela de jobs, idempotente por (tipo, chave).
 */
@ApplicationScoped
public class AgendaPersistente implements Agenda {

    private static final String INSERT = """
            INSERT INTO job (org_id, tipo, chave, payload, executar_em)
            VALUES (?, ?, ?, cast(? as jsonb), ?)
            ON CONFLICT (tipo, chave) DO NOTHING
            """;

    private final EntityManager em;

    public AgendaPersistente(EntityManager em) {
        this.em = em;
    }

    @Override
    public void agendar(TarefaAgendada t) {
        OffsetDateTime quando = t.executarEm().withOffsetSameInstant(java.time.ZoneOffset.UTC);
        em.createNativeQuery(INSERT)
                .setParameter(1, t.org().valor())
                .setParameter(2, t.tipo())
                .setParameter(3, t.chave())
                .setParameter(4, t.payloadJson() == null ? "{}" : t.payloadJson())
                .setParameter(5, quando)
                .executeUpdate();
    }
}
