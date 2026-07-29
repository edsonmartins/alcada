package app.alcada.captura;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import app.alcada.captura.internal.ProcessadorGrupo;
import app.alcada.plataforma.gateway.TransporteFake;
import app.alcada.plataforma.gateway.internal.TransporteModelo.Status;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** F2 (2/2) — janela de grupo → pendência na Entrada (extração via gateway fake). */
@QuarkusTest
class ProcessadorGrupoTest {

    private static final String COMPROMISSO = """
            {"dependeDoGestor":true,"tipo":"REUNIAO",
             "assunto":"cronograma atualizado e próximos passos","quemPede":"organizadora",
             "quando":{"textoOriginal":"próxima segunda 14h","resolvido":"2026-08-03T14:00:00-03:00"},
             "acaoPendente":"reunião acordada; invite solicitado","possivelmenteFeito":true,"confianca":0.82}""";

    @Inject
    ProcessadorGrupo processador;
    @Inject
    TransporteFake transporte;
    @Inject
    EntityManager em;

    @BeforeEach
    void setup() {
        transporte.reset();
    }

    @Test
    void janela_de_grupo_com_decisao_vira_pendencia_na_entrada() {
        OrgId org = novaOrg();
        UUID fonte = criarFonte(org);
        String grupoId = "G-1@g.us";
        seed(org, fonte, grupoId, "5512999", "vamos marcar reunião de cronograma?");
        seed(org, fonte, grupoId, "5512888", "próxima segunda 14h, manda invite pf");
        transporte.programar(Status.OK, COMPROMISSO);

        processador.processar(org, grupoId);

        assertEquals(1L, contar(org,
                "SELECT count(*) FROM pendencia WHERE org_id = ? AND origem_thread = '" + grupoId
                        + "' AND classe = 'DECISAO' AND status = 'ENTRADA'"),
                "o compromisso do grupo vira pendência na Entrada");
    }

    @Test
    void nao_depende_do_gestor_nao_cria_pendencia() {
        OrgId org = novaOrg();
        UUID fonte = criarFonte(org);
        String grupoId = "G-2@g.us";
        seed(org, fonte, grupoId, "5512999", "bom dia pessoal");
        transporte.programar(Status.OK,
                "{\"dependeDoGestor\":false,\"tipo\":\"OUTRO\",\"assunto\":\"papo\",\"confianca\":0.9}");

        processador.processar(org, grupoId);

        assertEquals(0L, contar(org,
                "SELECT count(*) FROM pendencia WHERE org_id = ? AND origem_thread = '" + grupoId + "'"),
                "sem decisão que dependa do gestor → nada na Entrada");
    }

    @Test
    void segunda_janela_do_mesmo_grupo_e_cobranca_esquenta_e_nao_duplica() {
        OrgId org = novaOrg();
        UUID fonte = criarFonte(org);
        String grupoId = "G-3@g.us";
        seed(org, fonte, grupoId, "5512999", "e aí, decide?");
        transporte.programar(Status.OK, COMPROMISSO);
        processador.processar(org, grupoId);

        seed(org, fonte, grupoId, "5512999", "estamos esperando, decidiu?");
        processador.processar(org, grupoId); // cobrança do mesmo grupo

        assertEquals(1L, contar(org,
                "SELECT count(*) FROM pendencia WHERE org_id = ? AND origem_thread = '" + grupoId + "'"),
                "cobrança funde: não duplica");
        assertEquals(1L, contar(org,
                "SELECT count(*) FROM pendencia WHERE org_id = ? AND origem_thread = '" + grupoId
                        + "' AND temperatura >= 1"),
                "cobrança esquenta o item");
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

    private long contar(OrgId org, String sql) {
        return ((Number) QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery(sql).setParameter(1, org.valor()).getSingleResult())).longValue();
    }
}
