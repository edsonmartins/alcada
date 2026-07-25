package app.alcada.plataforma.trilha;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * INV-11 — a trilha é append-only. Insere-se; nunca se altera nem apaga.
 * O bloqueio é provado no nível do banco pelo TRIGGER, usando uma conexão de
 * superusuário (para mostrar que não é só o REVOKE do role da aplicação).
 */
@QuarkusTest
class TrilhaImutavelTest {

    private static final String URL = "jdbc:postgresql://localhost:5432/alcada_test";

    @Inject
    Trilha trilha;

    @Test
    void insercao_de_trilha_funciona() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                trilha.registrar(new EventoTrilha(
                        org, UUID.randomUUID(), TipoEvento.CAPTADA,
                        Ator.sistemaMotor("captura"),
                        null, "ENTRADA",
                        "{\"canal\":\"whatsapp\"}", null)));
        // se chegou aqui, o INSERT append-only passou
    }

    @Test
    void update_na_trilha_e_bloqueado_pelo_trigger() throws Exception {
        semearLinha();
        try (Connection c = DriverManager.getConnection(URL, "postgres", "");
             Statement st = c.createStatement()) {
            SQLException ex = assertThrows(SQLException.class, () ->
                    st.executeUpdate("UPDATE trilha SET tipo='RESOLVIDA' WHERE org_id IS NOT NULL"));
            assertTrue(ex.getMessage().toLowerCase().contains("append-only"),
                    "esperava erro do trigger append-only, veio: " + ex.getMessage());
        }
    }

    @Test
    void delete_na_trilha_e_bloqueado_pelo_trigger() throws Exception {
        semearLinha();
        try (Connection c = DriverManager.getConnection(URL, "postgres", "");
             Statement st = c.createStatement()) {
            SQLException ex = assertThrows(SQLException.class, () ->
                    st.executeUpdate("DELETE FROM trilha WHERE org_id IS NOT NULL"));
            assertTrue(ex.getMessage().toLowerCase().contains("append-only"),
                    "esperava erro do trigger append-only, veio: " + ex.getMessage());
        }
    }

    private void semearLinha() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                trilha.registrar(new EventoTrilha(
                        org, UUID.randomUUID(), TipoEvento.CAPTADA,
                        Ator.sistemaMotor("captura"), null, "ENTRADA", null, null)));
    }
}
