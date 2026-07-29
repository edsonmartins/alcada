package app.alcada.captura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import app.alcada.captura.internal.ProcessadorGrupo;
import app.alcada.plataforma.gateway.TransporteFake;
import app.alcada.plataforma.gateway.internal.TransporteModelo.Status;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.triagem.internal.TriagemService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** F3 aprendizado (011): descartar item de grupo realimenta o filtro daquele grupo. */
@QuarkusTest
class AprendizadoGrupoTest {

    private static final String COMPROMISSO = """
            {"dependeDoGestor":true,"tipo":"DECISAO","assunto":"aprovar o cronograma",
             "quemPede":"organizadora","acaoPendente":"decidir e responder","confianca":0.8}""";

    @Inject
    ProcessadorGrupo processador;
    @Inject
    TriagemService triagem;
    @Inject
    TransporteFake transporte;
    @Inject
    EntityManager em;

    @BeforeEach
    void setup() {
        transporte.reset();
    }

    @Test
    void descartar_item_de_grupo_grava_sinal_pela_thread() {
        OrgId org = novaOrg();
        UUID fonte = criarFonte(org);
        String g = "G-apr1@g.us";
        seed(org, fonte, g, "5512999", "decide aí, aprova?");
        transporte.programar(Status.OK, COMPROMISSO);
        processador.processar(org, g);
        UUID id = uuid(org, "SELECT id FROM pendencia WHERE org_id = ? AND origem_thread = '" + g + "'");

        triagem.descartar(org, id, UUID.randomUUID());

        assertEquals(1L, contar(org,
                "SELECT count(*) FROM sinal_descarte WHERE org_id = ? AND chave = '" + g + "'"),
                "descarte de item de grupo aprende pela thread (não por origem_destino)");
    }

    @Test
    void grupo_com_descartes_acima_do_limiar_faz_novo_item_nascer_para_rever() {
        OrgId org = novaOrg();
        UUID fonte = criarFonte(org);
        String g = "G-apr2@g.us";
        sinalDescarte(org, g);
        sinalDescarte(org, g); // limiar padrão = 2
        seed(org, fonte, g, "5512999", "decide aí, aprova?");
        transporte.programar(Status.OK, COMPROMISSO);

        processador.processar(org, g);

        assertTrue(bool(org,
                "SELECT baixa_confianca FROM pendencia WHERE org_id = ? AND origem_thread = '" + g + "'"),
                "grupo já bastante descartado → novo item nasce 'rever', nunca dropado");
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

    private void sinalDescarte(OrgId org, String chave) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "INSERT INTO sinal_descarte (org_id, chave) VALUES (?, ?)")
                .setParameter(1, org.valor()).setParameter(2, chave).executeUpdate());
    }

    private long contar(OrgId org, String sql) {
        return ((Number) QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery(sql).setParameter(1, org.valor()).getSingleResult())).longValue();
    }

    private UUID uuid(OrgId org, String sql) {
        return (UUID) QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery(sql).setParameter(1, org.valor()).getSingleResult());
    }

    private boolean bool(OrgId org, String sql) {
        return (Boolean) QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery(sql).setParameter(1, org.valor()).getSingleResult());
    }
}
