package app.alcada.captura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import app.alcada.captura.internal.ExpurgoEventoBruto;
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

/** Invariantes de privacidade/tenant do acompanhamento de grupo (024): C7, C8, C10, C11. */
@QuarkusTest
class GruposInvariantesTest {

    /** O modelo devolve o TOKEN do remetente (PESSOA_1); a re-hidratação o resolve localmente. */
    private static final String COMPROMISSO = """
            {"dependeDoGestor":true,"tipo":"DECISAO","assunto":"aprovar o cronograma",
             "quemPede":"PESSOA_1","acaoPendente":"decidir e responder","confianca":0.8}""";

    @Inject
    ProcessadorGrupo processador;
    @Inject
    ExpurgoEventoBruto expurgo;
    @Inject
    TransporteFake transporte;
    @Inject
    EntityManager em;

    @BeforeEach
    void setup() {
        transporte.reset();
    }

    @Test
    void c7_minimizador_nao_vaza_identificador_direto() {
        OrgId org = novaOrg();
        UUID fonte = criarFonte(org);
        entidade(org, "PESSOA", "Marcello Marinho");
        String g = "G-c7@g.us";
        seed(org, fonte, g, "Marcello Marinho",
                "decide aí? meu email marcello.marinho@rioquality.com.br e fone 11987654321");
        transporte.programar(Status.OK, COMPROMISSO);

        processador.processar(org, g);

        String enviado = transporte.ultima().texto();
        assertFalse(enviado.contains("Marcello Marinho"), "nome completo não atravessa o minimizador");
        assertFalse(enviado.contains("rioquality"), "e-mail não atravessa");
        assertFalse(enviado.contains("987654321"), "telefone não atravessa");
        assertTrue(enviado.contains("PESSOA_1"), "remetente pseudonimizado");
        assertTrue(enviado.contains("[REDIGIDO]"), "identificadores diretos redigidos");
    }

    @Test
    void c8_dono_e_apresentado_por_primeiro_nome() {
        OrgId org = novaOrg();
        UUID fonte = criarFonte(org);
        entidade(org, "PESSOA", "Marcello Marinho");
        String g = "G-c8@g.us";
        seed(org, fonte, g, "Marcello Marinho", "decide aí, pode aprovar?");
        transporte.programar(Status.OK, COMPROMISSO);

        processador.processar(org, g);

        assertEquals("Marcello", texto(org,
                "SELECT quem_espera FROM pendencia WHERE org_id = ? AND origem_thread = '" + g + "'"),
                "terceiro aparece por primeiro nome, não pelo nome completo (emenda ADR-0011)");
    }

    @Test
    void c10_isolamento_entre_tenants() {
        OrgId a = novaOrg();
        OrgId b = novaOrg();
        UUID fonteA = criarFonte(a);
        UUID fonteB = criarFonte(b);
        String g = "G-comum@g.us"; // mesmo id de grupo em orgs diferentes
        seed(a, fonteA, g, "5512999", "decide aí, aprova?");
        seed(b, fonteB, g, "5512888", "decide aí, aprova?");
        transporte.programar(Status.OK, COMPROMISSO);

        processador.processar(a, g);
        processador.processar(b, g);

        assertEquals(1L, contar(a,
                "SELECT count(*) FROM pendencia WHERE org_id = ? AND origem_thread = '" + g + "'"),
                "org A tem só o seu item");
        assertEquals(1L, contar(b,
                "SELECT count(*) FROM pendencia WHERE org_id = ? AND origem_thread = '" + g + "'"),
                "org B tem só o seu item");
        UUID idA = uuid(a, "SELECT id FROM pendencia WHERE org_id = ? AND origem_thread = '" + g + "'");
        UUID idB = uuid(b, "SELECT id FROM pendencia WHERE org_id = ? AND origem_thread = '" + g + "'");
        assertNotEquals(idA, idB, "itens são distintos, nada cruza org_id (INV-15)");
    }

    @Test
    void c11_bruto_expira_em_30_dias_mas_o_fato_derivado_permanece() throws Exception {
        OrgId org = novaOrg();
        UUID fonte = criarFonte(org);
        String g = "G-c11@g.us";
        seed(org, fonte, g, "5512999", "decide aí, aprova?");
        transporte.programar(Status.OK, COMPROMISSO);
        processador.processar(org, g); // cria a pendência (o fato derivado)

        // Passaram-se mais de 30 dias: o bruto vence.
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "UPDATE evento_bruto SET expira_em = now() - interval '1 day' WHERE org_id = ? AND thread_ref = ?")
                .setParameter(1, org.valor()).setParameter(2, g).executeUpdate());
        expurgo.expurgar();

        assertEquals(0L, contar(org,
                "SELECT count(*) FROM evento_bruto WHERE org_id = ? AND thread_ref = '" + g + "'"),
                "o bruto foi expurgado (ADR-0011 §4)");
        assertEquals(1L, contar(org,
                "SELECT count(*) FROM pendencia WHERE org_id = ? AND origem_thread = '" + g + "'"),
                "o fato derivado (a pendência) permanece");
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

    private void entidade(OrgId org, String tipo, String nome) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "INSERT INTO entidade (org_id, tipo, nome_canonico) VALUES (?, ?, ?)")
                .setParameter(1, org.valor()).setParameter(2, tipo).setParameter(3, nome).executeUpdate());
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

    private String texto(OrgId org, String sql) {
        return (String) QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery(sql).setParameter(1, org.valor()).getSingleResult());
    }

    private UUID uuid(OrgId org, String sql) {
        return (UUID) QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery(sql).setParameter(1, org.valor()).getSingleResult());
    }
}
