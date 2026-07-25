package app.alcada.captura.internal;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import javax.sql.DataSource;
import org.jboss.logging.Logger;

/**
 * Expurgo do bruto por {@code expira_em} (ADR-0011: retenção curta, expurgo
 * automático). Varredura de manutenção cross-tenant — roda por JDBC direto,
 * fora do Hibernate, por isso não passa pelo {@code GuardaOrgId} (não há org_id
 * num expurgo por tempo). O tick não guarda estado em memória.
 */
@ApplicationScoped
public class ExpurgoEventoBruto {

    private static final Logger LOG = Logger.getLogger(ExpurgoEventoBruto.class);

    private final DataSource ds;

    public ExpurgoEventoBruto(DataSource ds) {
        this.ds = ds;
    }

    @Scheduled(cron = "0 30 3 * * ?", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        try {
            int removidos = expurgar();
            if (removidos > 0) {
                LOG.infof("expurgo de evento_bruto: %d linha(s) expiradas removidas", removidos);
            }
        } catch (SQLException ex) {
            LOG.error("falha no expurgo de evento_bruto", ex);
        }
    }

    /** Remove eventos brutos vencidos. Retorna quantos foram removidos. */
    public int expurgar() throws SQLException {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement()) {
            int removidos = st.executeUpdate("DELETE FROM evento_bruto WHERE expira_em < now()");
            if (!c.getAutoCommit()) {
                c.commit();
            }
            return removidos;
        }
    }
}
