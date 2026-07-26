package app.alcada.assistente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import app.alcada.assistente.port.Dossie;
import app.alcada.assistente.port.RespostaDossie;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** 014 — Perguntas ao dossiê (índice híbrido BM25 + pgvector). */
@QuarkusTest
class DossieTest {

    @Inject EntityManager em;
    @Inject Dossie dossie;

    @Test
    void bm25_recupera_passagem_com_fonte() {
        OrgId org = novaOrg();
        UUID p = pendencia(org, "Aprovar reajuste do contrato do integrador logístico");
        RespostaDossie r = QuarkusTransaction.requiringNew().call(() -> dossie.perguntar(org, p, "reajuste do contrato"));
        assertTrue(r.encontrou());
        assertFalse(r.fontes().isEmpty(), "cita fonte (RFC-0004)");
        assertEquals("PENDENCIA", r.fontes().get(0).fonteTipo());
    }

    @Test
    void sem_passagem_nao_inventa() {
        OrgId org = novaOrg();
        UUID p = pendencia(org, "Aprovar reajuste do contrato");
        RespostaDossie r = QuarkusTransaction.requiringNew().call(() -> dossie.perguntar(org, p, "xyzzy quixotesco inexistente"));
        assertFalse(r.encontrou());
        assertTrue(r.resposta().toLowerCase().contains("não encontrei"));
        assertTrue(r.fontes().isEmpty());
    }

    @Test
    void isolamento_por_organizacao() {
        OrgId a = novaOrg();
        OrgId b = novaOrg();
        UUID pa = pendencia(a, "Contrato exclusivo do integrador Panorama");
        UUID pb = pendencia(b, "Assunto qualquer da org B");
        // B pergunta sobre conteúdo de A → não recupera
        RespostaDossie r = QuarkusTransaction.requiringNew().call(() -> dossie.perguntar(b, pb, "Panorama integrador"));
        assertFalse(r.encontrou(), "a pergunta de B nunca vê passagens de A");
    }

    // RFC-0004 §1 — correção de premissa: a base contradiz um fato da pergunta.
    @Test
    void corrige_premissa_de_data_contradita_pela_base() {
        OrgId org = novaOrg();
        UUID p = pendenciaComTrava(org, "Reajuste do contrato do integrador",
                "reajuste aprovado em 08/07/2026 pela diretoria");

        RespostaDossie r = QuarkusTransaction.requiringNew()
                .call(() -> dossie.perguntar(org, p, "o reajuste do contrato foi aprovado em maio?"));

        assertTrue(r.encontrou());
        assertTrue(r.correcao() != null && r.correcao().contains("08/07/2026"),
                "corrige citando a data da base: " + r.correcao());
        assertTrue(r.correcao().toLowerCase().contains("maio"), "aponta a premissa errada: " + r.correcao());
    }

    @Test
    void sem_contradicao_nao_ha_correcao() {
        OrgId org = novaOrg();
        UUID p = pendenciaComTrava(org, "Reajuste do contrato", "reajuste aprovado em 08/07/2026");
        RespostaDossie r = QuarkusTransaction.requiringNew()
                .call(() -> dossie.perguntar(org, p, "o reajuste foi aprovado em julho?"));
        assertTrue(r.encontrou());
        assertTrue(r.correcao() == null, "julho bate com a base: sem correção");
    }

    @Test
    void indexacao_idempotente() {
        OrgId org = novaOrg();
        UUID p = pendencia(org, "Aprovar reajuste");
        QuarkusTransaction.requiringNew().run(() -> dossie.indexar(org, p));
        QuarkusTransaction.requiringNew().run(() -> dossie.indexar(org, p));
        long n = QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                "SELECT count(*) FROM documento_indice WHERE org_id = ? AND pendencia_id = ? AND fonte_tipo = 'PENDENCIA'")
                .setParameter(1, org.valor()).setParameter(2, p).getSingleResult()).longValue());
        assertEquals(1, n, "reindexar substitui, não duplica");
    }

    @Test
    void cosseno_pgvector_recupera_o_mais_similar() {
        OrgId org = novaOrg();
        UUID p = pendencia(org, "base");
        // dois vetores ortogonais; a query casa com A
        inserirComEmb(org, p, "passagem A", eixo(0));
        inserirComEmb(org, p, "passagem B", eixo(1));
        String q = eixo(0);
        String texto = QuarkusTransaction.requiringNew().call(() -> (String) em.createNativeQuery("""
                SELECT texto FROM documento_indice WHERE org_id = ? AND emb IS NOT NULL
                ORDER BY emb <=> CAST(? AS vector) ASC LIMIT 1
                """).setParameter(1, org.valor()).setParameter(2, q).getSingleResult());
        assertEquals("passagem A", texto, "cosseno pgvector recupera o vetor mais próximo");
    }

    // ---- helpers -------------------------------------------------------------

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    private UUID pendencia(OrgId org, String titulo) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status)
                VALUES (?, ?, ?, 'DECISAO', 'SEMANA', 'AGENDADA')
                """).setParameter(1, id).setParameter(2, org.valor()).setParameter(3, titulo).executeUpdate());
        return id;
    }

    private UUID pendenciaComTrava(OrgId org, String titulo, String oQueTrava) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO pendencia (id, org_id, titulo, o_que_trava, classe, horizonte, status)
                VALUES (?, ?, ?, ?, 'DECISAO', 'SEMANA', 'AGENDADA')
                """).setParameter(1, id).setParameter(2, org.valor()).setParameter(3, titulo)
                .setParameter(4, oQueTrava).executeUpdate());
        return id;
    }

    private void inserirComEmb(OrgId org, UUID p, String texto, String emb) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO documento_indice (org_id, pendencia_id, fonte_tipo, fonte_ref, texto, emb)
                VALUES (?, ?, 'MENSAGEM', ?, ?, CAST(? AS vector))
                """).setParameter(1, org.valor()).setParameter(2, p).setParameter(3, UUID.randomUUID().toString())
                .setParameter(4, texto).setParameter(5, emb).executeUpdate());
    }

    /** Vetor 1024-dim unitário no eixo i. */
    private static String eixo(int i) {
        StringBuilder sb = new StringBuilder("[");
        for (int k = 0; k < 1024; k++) {
            if (k > 0) {
                sb.append(',');
            }
            sb.append(k == i ? "1" : "0");
        }
        return sb.append(']').toString();
    }
}
