package app.alcada.plataforma.trilha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoRegistrado;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Compensação como único mecanismo de correção (INV-11) e carga por tipo de
 * evento (ADR-0016).
 */
@QuarkusTest
class CompensacaoECargaTest {

    @Inject Trilha trilha;
    @Inject ConsultaTrilha consulta;

    @Test
    void correcao_nao_altera_o_original() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID pend = UUID.randomUUID();
        UUID[] ids = new UUID[2];

        QuarkusTransaction.requiringNew().run(() -> {
            ids[0] = trilha.registrar(new EventoTrilha(
                    org, pend, TipoEvento.RESOLVIDA, Ator.humano(UUID.randomUUID()),
                    "ENTRADA", "FECHADA", null, null));
            ids[1] = trilha.compensar(org, pend, Ator.humano(UUID.randomUUID()),
                    ids[0], "resolvido por engano");
        });

        List<EventoRegistrado> eventos =
                QuarkusTransaction.requiringNew().call(() -> consulta.daPendencia(org, pend));

        assertEquals(2, eventos.size(), "original + compensação");
        EventoRegistrado original = eventos.stream()
                .filter(e -> e.id().equals(ids[0])).findFirst().orElseThrow();
        assertEquals("RESOLVIDA", original.tipo(), "o original permanece intacto");

        EventoRegistrado comp = eventos.stream()
                .filter(e -> e.tipo().equals("COMPENSACAO")).findFirst().orElseThrow();
        assertTrue(comp.carga().contains(ids[0].toString()), "referencia o evento compensado");
    }

    @Test
    void execucao_por_ausencia_registra_carga_completa() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID pend = UUID.randomUUID();
        UUID deleg = UUID.randomUUID();
        String carga = "{\"delegacao_id\":\"" + deleg + "\",\"prazo\":\"2026-07-23T18:00:00Z\","
                + "\"proposta\":\"reajuste conforme indice contratual\",\"janela\":\"PT4H\","
                + "\"intervencoes\":[]}";

        QuarkusTransaction.requiringNew().run(() -> trilha.registrar(new EventoTrilha(
                org, pend, TipoEvento.EXECUTADA_POR_AUSENCIA, Ator.sistemaMotor("motor-autonomia"),
                "DELEGADA", "FECHADA", null, carga)));

        EventoRegistrado ev =
                QuarkusTransaction.requiringNew().call(() -> consulta.daPendencia(org, pend)).get(0);
        assertTrue(ev.carga().contains(deleg.toString()));
        assertTrue(ev.carga().contains("intervencoes"));
        assertTrue(ev.carga().contains("janela"));
    }

    @Test
    void identificador_direto_na_carga_e_rejeitado() {
        OrgId org = new OrgId(UUID.randomUUID());
        Throwable erro = assertThrows(Throwable.class, () -> QuarkusTransaction.requiringNew().run(() ->
                trilha.registrar(new EventoTrilha(
                        org, UUID.randomUUID(), TipoEvento.CAPTADA, Ator.sistemaMotor("captura"),
                        null, "ENTRADA", null, "{\"contato\":\"fulano@example.com\"}"))));
        assertTrue(mensagemContem(erro, "identificador direto"), "veio: " + erro);
    }

    @Test
    void referencia_por_id_e_aceita() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID pend = UUID.randomUUID();
        UUID referenciaNumerica = UUID.fromString("12345678-abcd-4abc-8abc-123456789012");
        QuarkusTransaction.requiringNew().run(() -> trilha.registrar(new EventoTrilha(
                org, pend, TipoEvento.ROTEADA_POR_REGRA, Ator.sistemaRegra("bloqueio_tecnico"),
                "ENTRADA", "DELEGADA", null, "{\"pessoa_id\":\"" + referenciaNumerica + "\"}")));
        assertEquals(1, QuarkusTransaction.requiringNew().call(() -> consulta.daPendencia(org, pend)).size());
    }

    private static boolean mensagemContem(Throwable t, String trecho) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c.getMessage() != null && c.getMessage().contains(trecho)) {
                return true;
            }
        }
        return false;
    }
}
