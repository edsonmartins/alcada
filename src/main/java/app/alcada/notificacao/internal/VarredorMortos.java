package app.alcada.notificacao.internal;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/**
 * Varredor de mortos (decisão 1b): o {@code WorkerOutbox} permanece agnóstico da
 * trilha; este componente lê o outbox em {@code ERRO} ainda não marcado e grava
 * {@code FALHA_COMUNICACAO}. Idempotente por {@code falha_registrada} —
 * reexecutar não duplica.
 */
@ApplicationScoped
public class VarredorMortos {

    private static final Logger LOG = Logger.getLogger(VarredorMortos.class);
    private static final int LOTE = 100;

    private static final String CLAIM = """
            SELECT id, org_id, tipo, payload::text
            FROM outbox
            WHERE status = 'ERRO' AND NOT falha_registrada
            ORDER BY criado_em
            FOR UPDATE SKIP LOCKED
            LIMIT ?
            """;

    private final EntityManager em;
    private final Trilha trilha;
    private final ObjectMapper json = new ObjectMapper();

    public VarredorMortos(EntityManager em, Trilha trilha) {
        this.em = em;
        this.trilha = trilha;
    }

    @Scheduled(every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        try {
            varrer();
        } catch (RuntimeException e) {
            LOG.error("falha no varredor de mortos", e);
        }
    }

    @Transactional
    public int varrer() {
        @SuppressWarnings("unchecked")
        List<Object[]> mortos = em.createNativeQuery(CLAIM).setParameter(1, LOTE).getResultList();
        int registrados = 0;
        for (Object[] l : mortos) {
            UUID id = (UUID) l[0];
            UUID org = (UUID) l[1];
            String tipo = (String) l[2];
            UUID pendenciaId = pendenciaDe((String) l[3]);

            if (pendenciaId != null) {
                trilha.registrar(new EventoTrilha(new OrgId(org), pendenciaId, TipoEvento.FALHA_COMUNICACAO,
                        Ator.sistemaMotor("notificacao"), null, null, null,
                        "{\"tipo\":\"" + tipo + "\"}"));
                registrados++;
            }
            em.createNativeQuery("UPDATE outbox SET falha_registrada = true WHERE id = ? AND org_id = ?")
                    .setParameter(1, id).setParameter(2, org).executeUpdate();
        }
        return registrados;
    }

    private UUID pendenciaDe(String payload) {
        try {
            var n = json.readTree(payload).path("pendencia_id");
            return n.isMissingNode() || n.isNull() ? null : UUID.fromString(n.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
