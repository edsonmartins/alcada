package app.alcada.assistente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.NoSuchElementException;
import java.util.UUID;

import app.alcada.assistente.port.Bloco;
import app.alcada.assistente.port.BlocoDados;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** 013 — Bloco de decisão (dossiê + redação). */
@QuarkusTest
class BlocoTest {

    @Inject EntityManager em;
    @Inject Bloco bloco;

    @Test
    void montar_traz_dossie_e_opcoes_por_classe() {
        OrgId org = novaOrg();
        UUID p = pendencia(org, "Aprovar reajuste", "DECISAO");
        BlocoDados b = QuarkusTransaction.requiringNew().call(() -> bloco.montar(org, p));
        assertTrue(b.dossie().stream().anyMatch(d -> d.rotulo().equals("Quem espera")));
        assertTrue(b.opcoes().stream().anyMatch(o -> o.chave().equals("aprovar")));
        assertTrue(b.opcoes().stream().anyMatch(o -> o.chave().equals("recusar")));
    }

    @Test
    void redigir_devolve_rascunho_editavel() {
        OrgId org = novaOrg();
        UUID p = pendencia(org, "Aprovar reajuste", "DECISAO");
        var r = QuarkusTransaction.requiringNew().call(() -> bloco.redigir(org, p, "Aprovar", "direto"));
        assertNotNull(r.rascunho(), "sempre há rascunho editável (mesmo sem modelo)");
    }

    @Test
    void decidir_fecha_registra_e_enfileira() {
        OrgId org = novaOrg();
        UUID p = pendencia(org, "Aprovar reajuste", "DECISAO");
        QuarkusTransaction.requiringNew().run(() ->
                bloco.decidir(org, p, "Aprovar", "Aprovado conforme índice.", UUID.randomUUID()));

        assertEquals("FECHADA", QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT status FROM pendencia WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, p).getSingleResult()));
        assertEquals(1L, contar(org, "SELECT count(*) FROM trilha WHERE org_id = ? AND pendencia_id = ? AND tipo = 'DECIDIDA_NO_BLOCO'", p));
        assertEquals(1L, contar(org, "SELECT count(*) FROM outbox WHERE org_id = ? AND tipo = 'decisao.comunicada'", null));
    }

    @Test
    void decidir_ja_fechada_e_recusado() {
        OrgId org = novaOrg();
        UUID p = pendencia(org, "Aprovar reajuste", "DECISAO");
        QuarkusTransaction.requiringNew().run(() -> bloco.decidir(org, p, "Aprovar", "ok", null));
        assertThrows(IllegalStateException.class, () ->
                QuarkusTransaction.requiringNew().run(() -> bloco.decidir(org, p, "Aprovar", "de novo", null)));
    }

    @Test
    void isolamento_por_organizacao() {
        OrgId a = novaOrg();
        OrgId b = novaOrg();
        UUID p = pendencia(a, "Da A", "DECISAO");
        assertThrows(NoSuchElementException.class, () ->
                QuarkusTransaction.requiringNew().call(() -> bloco.montar(b, p)));
    }

    // ---- helpers -------------------------------------------------------------

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    private UUID pendencia(OrgId org, String titulo, String classe) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO pendencia (id, org_id, titulo, quem_espera, o_que_trava, valor_em_jogo, classe, horizonte, status)
                VALUES (?, ?, ?, 'Comercial', 'contrato vencendo', 42000, ?, 'SEMANA', 'AGENDADA')
                """).setParameter(1, id).setParameter(2, org.valor()).setParameter(3, titulo)
                .setParameter(4, classe).executeUpdate());
        return id;
    }

    private long contar(OrgId org, String sql, UUID p) {
        return QuarkusTransaction.requiringNew().call(() -> {
            var q = em.createNativeQuery(sql).setParameter(1, org.valor());
            if (p != null) {
                q.setParameter(2, p);
            }
            return ((Number) q.getSingleResult()).longValue();
        });
    }
}
