package app.alcada.plataforma.trilha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import app.alcada.plataforma.trilha.port.TipoEvento;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/**
 * Vocabulário fechado da trilha (anexo ADR-0016): tipo e ator fora do formato
 * são rejeitados pelo banco. Descarte por irrelevância não é evento de trilha.
 */
@QuarkusTest
class TrilhaVocabularioTest {

    private static final String INSERT =
            "INSERT INTO trilha (id, org_id, pendencia_id, tipo, ator, ocorrido_em) "
            + "VALUES (?, ?, ?, ?, ?, now())";

    @Inject
    EntityManager em;

    @Test
    void tipo_fora_do_vocabulario_fechado_e_rejeitado() {
        assertThrows(Throwable.class, () -> QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery(INSERT)
                        .setParameter(1, UUID.randomUUID())
                        .setParameter(2, UUID.randomUUID())
                        .setParameter(3, UUID.randomUUID())
                        .setParameter(4, "INVENTADO")            // fora dos 29 do anexo
                        .setParameter(5, "SISTEMA:motor:captura")
                        .executeUpdate()));
    }

    @Test
    void ator_fora_do_formato_e_rejeitado() {
        assertThrows(Throwable.class, () -> QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery(INSERT)
                        .setParameter(1, UUID.randomUUID())
                        .setParameter(2, UUID.randomUUID())
                        .setParameter(3, UUID.randomUUID())
                        .setParameter(4, "CAPTADA")
                        .setParameter(5, "fulano")               // não casa nenhum formato de ator
                        .executeUpdate()));
    }

    @Test
    void descarte_automatico_da_captura_nao_e_evento_de_trilha() {
        // O anexo define que o descarte AUTOMÁTICO por irrelevância vai para métrica
        // de captura (descarte_captura), não gera trilha. O único evento de descarte
        // no vocabulário é o DESCARTADA — decisão MANUAL de triagem (001), auditável
        // (INV-11). Não há tipo de descarte de captura.
        long descartes = java.util.Arrays.stream(TipoEvento.values())
                .filter(t -> t.name().contains("DESCART")).count();
        assertEquals(1, descartes, "só o descarte manual da triagem é evento de trilha");
        assertTrue(java.util.Arrays.stream(TipoEvento.values()).anyMatch(t -> t == TipoEvento.DESCARTADA));
    }
}
