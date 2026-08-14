package app.alcada.notificacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import app.alcada.autonomia.internal.MotorAutonomia;
import app.alcada.autonomia.port.ContatosExternos;
import app.alcada.autonomia.port.CorrelacoesRetorno;
import app.alcada.autonomia.port.DestinoRepasse;
import app.alcada.notificacao.internal.EmailStub;
import app.alcada.notificacao.internal.LinktorStub;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.internal.WorkerOutbox;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoRegistrado;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Entrega do aviso de repasse a contato externo (RFC-0008, fatia F1.3a). */
@QuarkusTest
class RepasseAvisoTest {

    @Inject MotorAutonomia motor;
    @Inject ContatosExternos contatos;
    @Inject CorrelacoesRetorno correlacoes;
    @Inject WorkerOutbox worker;
    @Inject LinktorStub linktor;
    @Inject EmailStub emailStub;
    @Inject ConsultaTrilha trilha;
    @Inject EntityManager em;

    @BeforeEach
    void limpar() {
        linktor.limpar();
        emailStub.limpar();
    }

    // WHEN repasse externo (WhatsApp) THEN o worker entrega no canal + trilha COMUNICADA
    @Test
    void aviso_externo_entrega_no_canal_e_registra_comunicada() {
        Ctx c = novo("chan-abc");
        UUID contato = contatos.registrar(c.org, "Clécia", "WHATSAPP", "+5521999990000", c.gestor);
        motor.delegar(c.org, c.pend, new DestinoRepasse.Externo(contato), "N2", agora(), c.gestor);

        worker.processarLote();

        var diretas = linktor.diretas().stream().filter(d -> "+5521999990000".equals(d.to())).toList();
        assertEquals(1, diretas.size(), "enviou 1 mensagem direta ao contato");
        assertEquals("chan-abc", diretas.get(0).channelId(), "usa o canal WhatsApp do tenant");
        assertNotNull(diretas.get(0).correlacao(), "propaga correlação opaca no metadata");
        assertTrue(diretas.get(0).texto().contains(c.pend.toString()), "texto referencia a pendência");
        assertTrue(tipos(c.org, c.pend).contains("COMUNICADA"));
    }

    @Test
    void aviso_externo_inclui_contexto_do_gestor_e_orienta_resposta() {
        Ctx c = novo("chan-contexto");
        UUID contato = contatos.registrar(c.org, "Edson", "WHATSAPP", "+554488122990", c.gestor);
        motor.delegar(c.org, c.pend, new DestinoRepasse.Externo(contato), "N3", agora(), c.gestor,
                "Confira os valores e me envie sua recomendação.");

        worker.processarLote();

        String texto = linktor.diretas().stream()
                .filter(d -> "+554488122990".equals(d.to())).findFirst().orElseThrow().texto();
        assertTrue(texto.contains("Confira os valores e me envie sua recomendação."));
        assertTrue(texto.contains("aguarde a aprovação"), "explica o significado do N3");
        assertTrue(texto.contains("citando-a"), "ensina como manter a resposta correlacionada");
    }

    @Test
    void retorno_valido_e_observado_minimizado_e_idempotente_sem_executar_acao() {
        Ctx c = novo("chan-retorno");
        UUID contato = contatos.registrar(c.org, "Contato", "WHATSAPP", "+5521999990001", c.gestor);
        motor.delegar(c.org, c.pend, new DestinoRepasse.Externo(contato), "N2", agora(), c.gestor);
        worker.processarLote();

        UUID delegacao = delegacao(c);
        String token = correlacoes.tokenParaEnvio(c.org, delegacao).orElseThrow();
        var primeiro = correlacoes.receber(c.org, token, "WHATSAPP", "5521999990001", "msg-ret-1",
                "Fale comigo em pessoa@empresa.com ou 5521999990001");
        var repetido = correlacoes.receber(c.org, token, "WHATSAPP", "5521999990001", "msg-ret-1", "outra");

        assertEquals(CorrelacoesRetorno.Resultado.OBSERVADO, primeiro);
        assertEquals(CorrelacoesRetorno.Resultado.REPETIDO, repetido);
        assertEquals(1L, contar(c.org, "SELECT count(*) FROM retorno_delegacao WHERE org_id=?"));
        assertEquals(1L, contar(c.org, "SELECT count(*) FROM retorno_delegacao WHERE org_id=?"
                + " AND estado='OBSERVADO' AND trecho_minimizado LIKE '%<EMAIL>%'"
                + " AND trecho_minimizado LIKE '%<TELEFONE>%'") );
        assertTrue(tipos(c.org, c.pend).contains("RETORNO_RECEBIDO"));
        assertEquals(0L, contar(c.org, "SELECT count(*) FROM delegacao WHERE org_id=? AND retorno_pendente"),
                "modo observar não altera o estado operacional");
    }

    @Test
    void retorno_com_autor_ou_tenant_divergente_nao_correlaciona() {
        Ctx c = novo("chan-isolamento");
        UUID contato = contatos.registrar(c.org, "Contato", "WHATSAPP", "+5521999990002", c.gestor);
        motor.delegar(c.org, c.pend, new DestinoRepasse.Externo(contato), "N2", agora(), c.gestor);
        UUID delegacao = delegacao(c);
        String token = correlacoes.tokenParaEnvio(c.org, delegacao).orElseThrow();

        assertEquals(CorrelacoesRetorno.Resultado.AUTOR_DIVERGENTE,
                correlacoes.receber(c.org, token, "WHATSAPP", "5511000000000", "msg-x", "oi"));
        Ctx outra = novo("chan-outra");
        assertEquals(CorrelacoesRetorno.Resultado.NAO_CORRELACIONADO,
                correlacoes.receber(outra.org, token, "WHATSAPP", "5521999990002", "msg-y", "oi"));
        assertEquals(0L, contar(c.org, "SELECT count(*) FROM retorno_delegacao WHERE org_id=?"));
    }

    // WHEN repasse externo (e-mail) THEN o worker envia por SMTP + trilha COMUNICADA
    @Test
    void aviso_externo_email_envia_por_smtp_e_registra_comunicada() {
        Ctx c = novo("chan-abc");
        UUID contato = contatos.registrar(c.org, "Ana Paula", "EMAIL", "ana.paula@hotsales.com.br", c.gestor);
        motor.delegar(c.org, c.pend, new DestinoRepasse.Externo(contato), "N2", agora(), c.gestor);

        worker.processarLote();

        var enviados = emailStub.enviados().stream()
                .filter(e -> "ana.paula@hotsales.com.br".equals(e.to())).toList();
        assertEquals(1, enviados.size(), "enviou 1 e-mail ao contato");
        assertTrue(enviados.get(0).texto().contains(c.pend.toString()), "corpo referencia a pendência");
        assertTrue(tipos(c.org, c.pend).contains("COMUNICADA"));
    }

    // WHEN reprocessa THEN não reenvia (idempotente por idempotency_key)
    @Test
    void aviso_e_idempotente_no_reprocesso() {
        Ctx c = novo("chan-abc");
        UUID contato = contatos.registrar(c.org, "Paulo", "WHATSAPP", "+5521988880000", c.gestor);
        motor.delegar(c.org, c.pend, new DestinoRepasse.Externo(contato), "N2", agora(), c.gestor);

        worker.processarLote();
        worker.processarLote();

        assertEquals(1, linktor.diretas().stream().filter(d -> "+5521988880000".equals(d.to())).count());
    }

    // WHEN o canal falha THEN não comunica e o aviso fica pendente (reprocessa)
    @Test
    void canal_indisponivel_nao_comunica_e_reprocessa() {
        Ctx c = novo("chan-abc");
        UUID contato = contatos.registrar(c.org, "Daniel", "WHATSAPP", "+5521977770000", c.gestor);
        linktor.programarFalha("+5521977770000");
        motor.delegar(c.org, c.pend, new DestinoRepasse.Externo(contato), "N2", agora(), c.gestor);

        worker.processarLote();

        assertEquals(0, linktor.diretas().stream().filter(d -> "+5521977770000".equals(d.to())).count());
        assertFalse(tipos(c.org, c.pend).contains("COMUNICADA"), "não comunica se o canal falhou");
        assertTrue(pendentes(c.org) >= 1, "o aviso continua pendente para reprocesso");
    }

    // ---- helpers ----
    private record Ctx(OrgId org, UUID pend, UUID gestor) {}

    private Ctx novo(String channelId) {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID pend = UUID.randomUUID();
        UUID gestor = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("INSERT INTO organizacao (id, nome, sku) VALUES (?, 'Org', 'CLOUD')")
                    .setParameter(1, org.valor()).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status)
                    VALUES (?, ?, 'Aprovar algo', 'DECISAO', 'SEMANA', 'ENTRADA')
                    """)
                    .setParameter(1, pend).setParameter(2, org.valor()).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO fonte (id, org_id, tipo, identificador, ativa, segredo, linktor_channel_id)
                    VALUES (?, ?, 'WHATSAPP', 'grupo-teste', true, 'seg', ?)
                    """)
                    .setParameter(1, UUID.randomUUID()).setParameter(2, org.valor())
                    .setParameter(3, channelId).executeUpdate();
        });
        return new Ctx(org, pend, gestor);
    }

    private static OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private long pendentes(OrgId org) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT count(*) FROM outbox WHERE org_id = ? AND tipo = 'AVISO_REPASSE' AND status = 'PENDENTE'")
                .setParameter(1, org.valor()).getSingleResult())).longValue();
    }

    private UUID delegacao(Ctx c) {
        return QuarkusTransaction.requiringNew().call(() -> (UUID) em.createNativeQuery(
                "SELECT id FROM delegacao WHERE org_id=? AND pendencia_id=? ORDER BY criada_em DESC LIMIT 1")
                .setParameter(1, c.org.valor()).setParameter(2, c.pend).getSingleResult());
    }

    private long contar(OrgId org, String sql) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(sql)
                .setParameter(1, org.valor()).getSingleResult())).longValue();
    }

    private List<String> tipos(OrgId org, UUID pend) {
        return QuarkusTransaction.requiringNew().call(() ->
                trilha.daPendencia(org, pend).stream().map(EventoRegistrado::tipo).toList());
    }
}
