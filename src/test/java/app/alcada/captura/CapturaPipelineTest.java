package app.alcada.captura;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import app.alcada.captura.internal.Ingestao;
import app.alcada.captura.internal.ProcessadorCaptura;
import app.alcada.captura.port.MensagemRecebida;
import app.alcada.plataforma.gateway.TransporteFake;
import app.alcada.plataforma.gateway.internal.TransporteModelo.Status;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoRegistrado;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Os 7 cenários do spec.md do 001 (gateway/transporte fake). */
@QuarkusTest
class CapturaPipelineTest {

    @Inject Ingestao ingestao;
    @Inject ProcessadorCaptura processador;
    @Inject TransporteFake transporte;
    @Inject ConsultaTrilha consultaTrilha;
    @Inject EntityManager em;

    @BeforeEach
    void setup() {
        transporte.reset();
    }

    // ---- Cenário 1 ---------------------------------------------------------
    @Test
    void mensagem_em_grupo_declarado_vira_pendencia() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID fonte = criarOrgEFonte(org, "CLOUD", "WHATSAPP");
        transporte.programar(Status.OK, extracao("Reembolso Rafael", "Rafael", "aprovar reembolso", "DECISAO", 0.9));

        UUID ev = ingerir(org, fonte, "@alcada aprovar reembolso do Rafael", "joao");
        processador.processar(org, ev);

        Object[] p = pendenciaUnica(org);
        assertEquals("ENTRADA", p[1]);
        assertEquals("Rafael", p[2]);
        assertEquals("DECISAO", p[3]);
        assertTrue(tiposTrilha(org, (UUID) p[0]).contains("CAPTADA"));
        assertEquals(1L, contar(org, "SELECT count(*) FROM outbox WHERE org_id = ? AND tipo = 'canal.resposta'"));
    }

    // ---- Cenário 2 ---------------------------------------------------------
    @Test
    void mensagem_irrelevante_e_descartada() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID fonte = criarOrgEFonte(org, "CLOUD", "WHATSAPP");

        UUID ev = ingerir(org, fonte, "bom dia pessoal, tudo certo?", "joao");
        processador.processar(org, ev);

        assertEquals(0L, contar(org, "SELECT count(*) FROM pendencia WHERE org_id = ?"));
        assertEquals(1L, contar(org, "SELECT count(*) FROM descarte_captura WHERE org_id = ? AND motivo = 'SEM_RELEVANCIA'"));
        assertEquals(1L, contar(org, "SELECT count(*) FROM evento_bruto WHERE org_id = ? AND expira_em IS NOT NULL"));
    }

    // ---- Cenário 3 ---------------------------------------------------------
    @Test
    void recobranca_nao_cria_item() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID fonte = criarOrgEFonte(org, "CLOUD", "WHATSAPP");
        String ext = extracao("Contrato Panorama parado", "Rafael", "assinar contrato do Panorama", "DECISAO", 0.9);

        transporte.programar(Status.OK, ext);
        processador.processar(org, ingerir(org, fonte, "@alcada contrato do Panorama com Rafael", "joao"));
        // segunda mensagem: mesma entidade + texto idêntico → recobrança
        transporte.programar(Status.OK, ext);
        processador.processar(org, ingerir(org, fonte, "@alcada e o contrato do Panorama, Rafael?", "maria"));

        assertEquals(1L, contar(org, "SELECT count(*) FROM pendencia WHERE org_id = ?"), "não cria segunda pendência");
        assertEquals(1L, contar(org, "SELECT count(*) FROM cobranca WHERE org_id = ?"));
        assertEquals(1, ((Number) unica(org,
                "SELECT temperatura FROM pendencia WHERE org_id = ?")).intValue(), "temperatura incrementada");
    }

    // ---- Cenário 4 ---------------------------------------------------------
    @Test
    void fusao_indevida_e_revertida() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID fonte = criarOrgEFonte(org, "CLOUD", "WHATSAPP");
        String ext = extracao("Contrato Panorama", "Rafael", "assinar contrato Panorama", "DECISAO", 0.9);
        transporte.programar(Status.OK, ext);
        processador.processar(org, ingerir(org, fonte, "@alcada contrato Panorama do Rafael", "joao"));
        transporte.programar(Status.OK, ext);
        processador.processar(org, ingerir(org, fonte, "@alcada contrato Panorama do Rafael de novo", "maria"));

        UUID original = (UUID) unica(org, "SELECT id FROM pendencia WHERE org_id = ?");
        UUID cobranca = (UUID) unica(org, "SELECT id FROM cobranca WHERE org_id = ?");
        UUID pessoa = UUID.randomUUID();

        given()
                .header("X-Org-Id", org.valor().toString())
                .header("X-Pessoa-Id", pessoa.toString())
                .contentType("application/json")
                .body("{\"cobrancaId\":\"" + cobranca + "\"}")
        .when()
                .post("/v1/pendencias/" + original + "/desfundir")
        .then()
                .statusCode(200);

        assertEquals(0L, contar(org, "SELECT count(*) FROM cobranca WHERE org_id = ?"), "cobrança desvinculada");
        assertEquals(2L, contar(org, "SELECT count(*) FROM pendencia WHERE org_id = ?"), "pendência independente criada");
        assertTrue(tiposTrilha(org, original).contains("DESFUNDIDA"), "trilha registrada na original");
    }

    // ---- Cenário 5 ---------------------------------------------------------
    @Test
    void bloqueio_operacional_nao_chega_ao_gestor() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID fonte = criarOrgEFonte(org, "CLOUD", "WEBHOOK");
        UUID dono = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "INSERT INTO regra_autonomia (org_id, classe, nivel, dono_id) VALUES (?, 'BLOQUEIO', 'N1', ?)")
                .setParameter(1, org.valor()).setParameter(2, dono).executeUpdate());
        transporte.programar(Status.OK, extracao("Disco cheio srv-01", "time-infra", "liberar espaço", "BLOQUEIO", 0.95));

        UUID ev = ingerir(org, fonte, "alerta: disco cheio em srv-01", "monitor");
        processador.processar(org, ev);

        Object[] p = pendenciaUnica(org);
        assertEquals("DELEGADA", p[1], "roteada por regra para N1, não fica na entrada");
        assertTrue(tiposTrilha(org, (UUID) p[0]).contains("ROTEADA_POR_REGRA"));

        // não aparece em /entrada
        long naEntrada = contar(org, "SELECT count(*) FROM pendencia WHERE org_id = ? AND status = 'ENTRADA'");
        assertEquals(0L, naEntrada);
    }

    // ---- Cenário 6 ---------------------------------------------------------
    @Test
    void email_com_pendencia_enterrada_na_thread() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID fonte = criarOrgEFonte(org, "CLOUD", "EMAIL");
        // autor da mensagem é o último remetente; o solicitante real é outro
        transporte.programar(Status.OK, extracao("Aprovar orçamento", "Rafael", "aprovar orçamento", "DECISAO", 0.9));

        UUID ev = ingerir(org, fonte, "@alcada segue a thread... precisamos da aprovação", "ultimo.remetente");
        processador.processar(org, ev);

        Object[] p = pendenciaUnica(org);
        assertEquals("Rafael", p[2], "quem_espera é o solicitante real");
        assertNotEquals("ultimo.remetente", p[2], "não é o último remetente");
    }

    // ---- Cenário 7 ---------------------------------------------------------
    @Test
    void varredura_completa_e_impossivel() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID fonte = criarOrgEFonte(org, "CLOUD", "WHATSAPP");

        // mensagem fora dos critérios de relevância
        UUID ev = ingerir(org, fonte, "conversa aleatória sem menção", "joao");
        processador.processar(org, ev);

        assertEquals(0, transporte.chamadas(), "irrelevante nunca é submetida ao extrator (sem varredura)");
        assertEquals(1L, contar(org, "SELECT count(*) FROM descarte_captura WHERE org_id = ?"), "descarte auditável");
    }

    // ---- helpers -----------------------------------------------------------

    private UUID criarOrgEFonte(OrgId org, String sku, String tipoFonte) {
        UUID fonte = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("INSERT INTO organizacao (id, nome, sku) VALUES (?, ?, ?)")
                    .setParameter(1, org.valor()).setParameter(2, "Org").setParameter(3, sku).executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO fonte (id, org_id, tipo, identificador, segredo) VALUES (?, ?, ?, 'grupo-x', 's3gr3d0')")
                    .setParameter(1, fonte).setParameter(2, org.valor()).setParameter(3, tipoFonte).executeUpdate();
        });
        return fonte;
    }

    private UUID ingerir(OrgId org, UUID fonte, String texto, String autor) {
        return ingestao.ingerir(org, new MensagemRecebida(
                "canal", fonte.toString(), autor, "thread-1", texto, List.of(), UUID.randomUUID().toString()));
    }

    private static String extracao(String titulo, String quem, String trava, String classe, double conf) {
        return "{\"titulo\":\"" + titulo + "\",\"quem_espera\":\"" + quem + "\",\"o_que_trava\":\"" + trava
                + "\",\"prazo_implicito\":null,\"valor_em_jogo\":null,\"entidades\":[\"" + quem
                + "\"],\"classe_sugerida\":\"" + classe + "\",\"confianca\":" + conf + "}";
    }

    private Object[] pendenciaUnica(OrgId org) {
        return (Object[]) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT id, status, quem_espera, classe FROM pendencia WHERE org_id = ?")
                .setParameter(1, org.valor()).getSingleResult());
    }

    private Object unica(OrgId org, String sql) {
        return QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery(sql).setParameter(1, org.valor()).getSingleResult());
    }

    private long contar(OrgId org, String sql) {
        return ((Number) QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery(sql).setParameter(1, org.valor()).getSingleResult())).longValue();
    }

    private List<String> tiposTrilha(OrgId org, UUID pendenciaId) {
        return QuarkusTransaction.requiringNew().call(() ->
                consultaTrilha.daPendencia(org, pendenciaId).stream().map(EventoRegistrado::tipo).toList());
    }
}
