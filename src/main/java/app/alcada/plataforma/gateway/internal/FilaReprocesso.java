package app.alcada.plataforma.gateway.internal;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/**
 * Fila de reprocesso: indisponibilidade do gateway não perde a captura. Guarda
 * só a referência à mensagem (bruto no Linktor), nunca o texto.
 */
@ApplicationScoped
public class FilaReprocesso {

    private static final String INSERT = """
            INSERT INTO tarefa_reprocesso (org_id, tipo_tarefa, ref_mensagem_id)
            VALUES (?, ?, ?)
            """;

    private final EntityManager em;

    public FilaReprocesso(EntityManager em) {
        this.em = em;
    }

    public void enfileirar(OrgId org, String tipoTarefa, UUID refMensagemId) {
        em.createNativeQuery(INSERT)
                .setParameter(1, org.valor())
                .setParameter(2, tipoTarefa)
                .setParameter(3, refMensagemId)
                .executeUpdate();
    }
}
