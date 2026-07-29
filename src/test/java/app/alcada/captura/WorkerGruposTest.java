package app.alcada.captura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import app.alcada.captura.internal.WorkerGrupos;
import app.alcada.plataforma.gateway.TransporteFake;
import app.alcada.plataforma.gateway.internal.TransporteModelo.Status;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** F2 — varredura por janela: debounce (assentou?) + marca avaliado (não re-processa em loop). */
@QuarkusTest
class WorkerGruposTest {

    private static final String COMPROMISSO = """
            {"dependeDoGestor":true,"tipo":"DECISAO","assunto":"aprovar o cronograma",
             "quemPede":"organizadora","acaoPendente":"decidir e responder","confianca":0.8}""";

    @Inject
    WorkerGrupos worker;
    @Inject
    TransporteFake transporte;
    @Inject
    EntityManager em;

    @BeforeEach
    void setup() {
        transporte.reset();
    }

    @Test
    void grupo_assentado_com_conteudo_novo_e_avaliado_e_vira_pendencia() {
        OrgId org = novaOrg();
        UUID fonte = criarFonte(org);
        String g = "G-worker-1@g.us";
        ativarGrupo(org, fonte, g, "10 minutes"); // conversa já assentou
        seed(org, fonte, g, "5512999", "decide aí, pf?");
        transporte.programar(Status.OK, COMPROMISSO);

        int n = worker.varrer();

        assertTrue(n >= 1, "reservou o grupo assentado");
        assertEquals(1L, contar(org,
                "SELECT count(*) FROM pendencia WHERE org_id = ? AND origem_thread = '" + g + "'"),
                "janela do grupo vira pendência");
        assertEquals(0L, contar(org,
                "SELECT count(*) FROM grupo_acompanhado WHERE org_id = ? AND grupo_id = '" + g
                        + "' AND avaliado_em IS NULL"),
                "marcou avaliado_em: debounce não reprocessa sem conteúdo novo");
    }

    @Test
    void grupo_ainda_quente_nao_e_avaliado() {
        OrgId org = novaOrg();
        UUID fonte = criarFonte(org);
        String g = "G-worker-2@g.us";
        ativarGrupo(org, fonte, g, "0 seconds"); // acabaram de falar
        seed(org, fonte, g, "5512999", "decide aí?");
        transporte.programar(Status.OK, COMPROMISSO);

        worker.varrer();

        assertEquals(0L, contar(org,
                "SELECT count(*) FROM pendencia WHERE org_id = ? AND origem_thread = '" + g + "'"),
                "conversa não assentou → não processa ainda");
    }

    // ---- helpers -----------------------------------------------------------

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    private UUID criarFonte(OrgId org) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO fonte (id, org_id, tipo, identificador, segredo)
                VALUES (?, ?, 'WHATSAPP', 'grupo', 's')
                """).setParameter(1, id).setParameter(2, org.valor()).executeUpdate());
        return id;
    }

    private void ativarGrupo(OrgId org, UUID fonte, String grupoId, String vistoHa) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO grupo_acompanhado
                    (org_id, fonte_id, grupo_id, ativa, ultimo_visto)
                VALUES (?, ?, ?, true, now() - (? ::interval))
                """).setParameter(1, org.valor()).setParameter(2, fonte)
                .setParameter(3, grupoId).setParameter(4, vistoHa).executeUpdate());
    }

    private void seed(OrgId org, UUID fonte, String grupoId, String autor, String texto) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO evento_bruto
                    (id, org_id, fonte_id, mensagem_id, autor_ext, texto, thread_ref, expira_em, grupo)
                VALUES (?, ?, ?, ?, ?, ?, ?, now() + interval '30 days', true)
                """)
                .setParameter(1, UUID.randomUUID()).setParameter(2, org.valor()).setParameter(3, fonte)
                .setParameter(4, "m-" + UUID.randomUUID()).setParameter(5, autor)
                .setParameter(6, texto).setParameter(7, grupoId).executeUpdate());
    }

    private long contar(OrgId org, String sql) {
        return ((Number) QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery(sql).setParameter(1, org.valor()).getSingleResult())).longValue();
    }
}
