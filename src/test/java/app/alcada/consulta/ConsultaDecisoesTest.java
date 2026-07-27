package app.alcada.consulta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import app.alcada.consulta.port.Consulta;
import app.alcada.consulta.port.ResultadoConsulta;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/**
 * "O que eu decidi" (022, fatia C): lê a trilha filtrada pelo ator (o gestor), por
 * tipo de decisão e por período. Escopo por org (INV-15) + por gestor.
 */
@QuarkusTest
class ConsultaDecisoesTest {

    @Inject Consulta consulta;
    @Inject Trilha trilha;
    @Inject EntityManager em;

    @Test
    void contaAsDecisoesDoGestorNoPeriodoIgnorandoOutroGestor() {
        OrgId org = novaOrg();
        UUID gestor = UUID.randomUUID();
        UUID outro = UUID.randomUUID();
        decisao(org, gestor, TipoEvento.RESOLVIDA);
        decisao(org, gestor, TipoEvento.REPASSADA);
        decisao(org, outro, TipoEvento.RESOLVIDA);      // outro gestor — não conta
        evento(org, gestor, TipoEvento.CAPTADA);        // não é decisão — não conta

        ResultadoConsulta r = QuarkusTransaction.requiringNew().call(
                () -> consulta.consultar(org, gestor, "o que eu decidi esta semana"));
        assertEquals("DECISOES_RECENTES", r.template());
        assertEquals(2, r.itens().size());
        assertTrue(r.resposta().contains("2"), r.resposta());
    }

    @Test
    void semGestorAvisaQueNaoIdentificou() {
        OrgId org = novaOrg();
        ResultadoConsulta r = QuarkusTransaction.requiringNew().call(
                () -> consulta.consultar(org, null, "o que eu decidi"));
        assertEquals("DECISOES_RECENTES", r.template());
        assertTrue(r.resposta().toLowerCase().contains("identificar"), r.resposta());
    }

    @Test
    void isoladoPorOrganizacao() {
        OrgId a = novaOrg();
        OrgId b = novaOrg();
        UUID gestor = UUID.randomUUID();
        decisao(a, gestor, TipoEvento.RESOLVIDA);

        ResultadoConsulta r = QuarkusTransaction.requiringNew().call(
                () -> consulta.consultar(b, gestor, "o que eu decidi"));
        assertEquals(0, r.itens().size(), "trilha de outra org não vaza (INV-15)");
    }

    // ---- helpers -----------------------------------------------------------

    private void decisao(OrgId org, UUID gestor, TipoEvento tipo) {
        evento(org, gestor, tipo);
    }

    private void evento(OrgId org, UUID gestor, TipoEvento tipo) {
        UUID pend = pendencia(org);
        QuarkusTransaction.requiringNew().run(() -> trilha.registrar(new EventoTrilha(
                org, pend, tipo, Ator.humano(gestor), "ENTRADA", "FECHADA", null, null)));
    }

    private UUID pendencia(OrgId org) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status, valor_em_jogo)
                VALUES (?, ?, 'Item', 'DECISAO', 'SEMANA', 'FECHADA', 1000)
                """).setParameter(1, id).setParameter(2, org.valor()).executeUpdate());
        return id;
    }

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }
}
