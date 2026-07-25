package app.alcada.plataforma.trilha.internal;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoRegistrado;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

/**
 * Consulta da trilha por pendência, em ordem cronológica. O predicado carrega
 * {@code org_id} (INV-15) — o {@code GuardaOrgId} recusaria a query sem ele.
 */
@ApplicationScoped
public class ConsultaTrilhaJdbc implements ConsultaTrilha {

    private static final String SELECT = """
            SELECT id, pendencia_id, tipo, ator, ocorrido_em,
                   estado_anterior, estado_posterior, origem::text, carga::text
            FROM trilha
            WHERE org_id = ? AND pendencia_id = ?
            ORDER BY ocorrido_em, id
            """;

    private final EntityManager em;

    public ConsultaTrilhaJdbc(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<EventoRegistrado> daPendencia(OrgId org, UUID pendenciaId) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = em.createNativeQuery(SELECT)
                .setParameter(1, org.valor())
                .setParameter(2, pendenciaId)
                .getResultList();

        List<EventoRegistrado> eventos = new ArrayList<>(linhas.size());
        for (Object[] l : linhas) {
            eventos.add(new EventoRegistrado(
                    (UUID) l[0],
                    (UUID) l[1],
                    (String) l[2],
                    (String) l[3],
                    ((java.time.OffsetDateTime) toOffset(l[4])),
                    (String) l[5],
                    (String) l[6],
                    (String) l[7],
                    (String) l[8]));
        }
        return eventos;
    }

    private static OffsetDateTime toOffset(Object o) {
        if (o instanceof OffsetDateTime odt) {
            return odt;
        }
        if (o instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        if (o instanceof java.time.Instant i) {
            return i.atOffset(java.time.ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(o.toString());
    }
}
