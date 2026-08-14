package app.alcada.movel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import app.alcada.autonomia.port.ContatosExternos;
import app.alcada.identidade.port.Preferencias;
import app.alcada.movel.port.Comando;
import app.alcada.movel.port.Comando.Campos;
import app.alcada.movel.port.Comando.Contato;
import app.alcada.movel.port.Comando.Intencao;
import app.alcada.movel.port.ComandoMovel;
import app.alcada.movel.port.ResultadoComando;
import app.alcada.movel.port.ResultadoComando.Status;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/**
 * Repasse do canal móvel com destino externo (RFC-0008, fatia F1.4b): o comando
 * REPASSAR aceita um contato conhecido ou registra um na hora, e delega por
 * {@code DestinoRepasse.Externo} — o aviso ao contato sai pelo outbox.
 */
@QuarkusTest
class ComandoRepasseExternoTest {

    @Inject ComandoMovel comandos;
    @Inject ContatosExternos contatos;
    @Inject Preferencias preferencias;
    @Inject EntityManager em;

    // C1 — repasse para contato externo já conhecido
    @Test
    void repassa_para_contato_existente() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        UUID gestor = UUID.randomUUID();
        UUID contato = contatos.registrar(org, "Clécia", "WHATSAPP", "+5521999990000", gestor);

        var r = sync(org, gestor, repassar(pend, null, new Contato(contato, null, null, null), null));

        assertEquals(Status.OK, r.get(0).status());
        assertEquals("DELEGADA", status(org, pend));
        assertEquals(contato, contatoDaDelegacao(org, pend), "delegação aponta para o contato");
        assertNull(donoDaDelegacao(org, pend), "externo não grava dono_id");
        assertEquals(1L, countOutbox(org, "AVISO_REPASSE"), "o contato é avisado pelo canal");
    }

    // C2 — repasse para contato novo: registra e delega no mesmo comando (escape, INV-02)
    @Test
    void repassa_para_contato_novo_registrando_na_hora() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        UUID gestor = UUID.randomUUID();

        var r = sync(org, gestor, repassar(pend, null,
                new Contato(null, "Marcello", "WHATSAPP", "+5521988887777"), null));

        assertEquals(Status.OK, r.get(0).status());
        assertEquals("DELEGADA", status(org, pend));
        List<ContatosExternos.ContatoExterno> lista = contatos.listar(org);
        assertEquals(1, lista.size(), "o contato novo foi registrado");
        assertEquals("Marcello", lista.get(0).nome());
        assertEquals(lista.get(0).id(), contatoDaDelegacao(org, pend));
        assertEquals(1L, countOutbox(org, "AVISO_REPASSE"));
    }

    // C3 — reenvio do mesmo comandoId não cria segundo contato nem segundo aviso (INV-13)
    @Test
    void reenvio_do_mesmo_comando_nao_duplica_contato_nem_aviso() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        UUID gestor = UUID.randomUUID();
        Comando c = repassar(pend, null, new Contato(null, "Marcello", "EMAIL",
                "marcello@rioquality.com.br"), null);

        sync(org, gestor, c);
        var r2 = sync(org, gestor, c);

        assertEquals(Status.OK, r2.get(0).status(), "reenvio devolve o resultado gravado");
        assertEquals(1, contatos.listar(org).size(), "um contato só");
        assertEquals(1L, countOutbox(org, "AVISO_REPASSE"), "um aviso só");
        assertEquals(1L, countDelegacoes(org, pend));
    }

    // C4 — dono e contato juntos: o repasse tem um destino só
    @Test
    void dono_e_contato_juntos_e_recusado() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        UUID gestor = UUID.randomUUID();
        UUID contato = contatos.registrar(org, "Clécia", "WHATSAPP", "+5521999990000", gestor);

        var r = sync(org, gestor, repassar(pend, UUID.randomUUID(),
                new Contato(contato, null, null, null), null));

        assertEquals(Status.ERRO, r.get(0).status());
        assertTrue(r.get(0).detalhe().contains("não os dois"), r.get(0).detalhe());
        assertEquals("ENTRADA", status(org, pend), "nada foi despachado");
        assertEquals(0L, countDelegacoes(org, pend));
    }

    // C5 — sem dono e sem contato: não há destino
    @Test
    void repasse_sem_destino_e_recusado() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");

        var r = sync(org, UUID.randomUUID(), repassar(pend, null, null, null));

        assertEquals(Status.ERRO, r.get(0).status());
        assertEquals("REPASSAR exige dono ou contato", r.get(0).detalhe());
        assertEquals("ENTRADA", status(org, pend));
    }

    // C6 — contato novo com canal inválido: recusa sem gravar nada
    @Test
    void contato_novo_com_canal_invalido_e_recusado() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");

        var r = sync(org, UUID.randomUUID(), repassar(pend, null,
                new Contato(null, "Marcello", "SMS", "+5521988887777"), null));

        assertEquals(Status.ERRO, r.get(0).status());
        assertTrue(contatos.listar(org).isEmpty(), "contato inválido não é registrado");
        assertEquals("ENTRADA", status(org, pend));
        assertEquals(0L, countOutbox(org, "AVISO_REPASSE"));
    }

    // C6b — contato novo sem canal: recusa com o motivo, não com um erro interno
    @Test
    void contato_novo_sem_canal_e_recusado_com_motivo() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");

        var r = sync(org, UUID.randomUUID(), repassar(pend, null,
                new Contato(null, "Marcello", null, "+5521988887777"), null));

        assertEquals(Status.ERRO, r.get(0).status());
        assertTrue(r.get(0).detalhe().startsWith("contato novo exige"), r.get(0).detalhe());
        assertTrue(contatos.listar(org).isEmpty());
        assertEquals("ENTRADA", status(org, pend));
    }

    // C6c — contato sem nenhum campo ({} no JSON) é ausência de destino, não um destino
    @Test
    void contato_vazio_nao_conta_como_destino() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        UUID dono = UUID.randomUUID();
        Contato vazio = new Contato(null, null, null, null);

        var interno = sync(org, UUID.randomUUID(), repassar(pend, dono, vazio, null));
        assertEquals(Status.OK, interno.get(0).status(), "dono + contato vazio é repasse interno");
        assertEquals(dono, donoDaDelegacao(org, pend));

        UUID outra = pendencia(org, "ENTRADA");
        var semDestino = sync(org, UUID.randomUUID(), repassar(outra, null, vazio, null));
        assertEquals("REPASSAR exige dono ou contato", semDestino.get(0).detalhe());
    }

    // C7 — contato de outra organização não existe para este tenant (INV-15)
    @Test
    void contato_de_outra_organizacao_nao_e_encontrado() {
        OrgId a = novaOrg();
        OrgId b = novaOrg();
        UUID pend = pendencia(a, "ENTRADA");
        UUID contatoDeB = contatos.registrar(b, "Paulo", "EMAIL", "paulo@rioquality.com.br",
                UUID.randomUUID());

        var r = sync(a, UUID.randomUUID(), repassar(pend, null,
                new Contato(contatoDeB, null, null, null), null));

        assertEquals(Status.ERRO, r.get(0).status());
        assertEquals("contato não encontrado", r.get(0).detalhe());
        assertEquals("ENTRADA", status(a, pend));
        assertEquals(0L, countDelegacoes(a, pend));
    }

    // C8 — no destino externo, o termo falado não vira apelido de pessoa; o nível vira hábito
    @Test
    void repasse_externo_nao_aprende_apelido_de_pessoa() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        UUID gestor = UUID.randomUUID();

        var r = sync(org, gestor, repassar(pend, null,
                new Contato(null, "Marcello", "WHATSAPP", "+5521988887777"), "marcelo da qualidade"));

        assertEquals(Status.OK, r.get(0).status());
        assertEquals(0L, countApelidos(org), "apelido de contato externo não é apelido de pessoa");
        assertEquals("N2", preferencias.nivelRepasse(org, gestor).orElse(null),
                "o nível usado continua virando o padrão do gestor");
    }

    // C9 — ditado em trajeto: o aviso ao contato nasce represado (INV-14, ADR-0014)
    @Test
    void aviso_de_repasse_em_trajeto_nasce_represado() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        UUID trajeto = UUID.randomUUID();
        Comando c = new Comando(UUID.randomUUID(), Intencao.REPASSAR, pend,
                campos(null, new Contato(null, "Marcello", "WHATSAPP", "+5521988887777"), null),
                trajeto);

        var r = comandos.sincronizar(org, UUID.randomUUID(), List.of(c));

        assertEquals(Status.OK, r.get(0).status());
        assertEquals(trajeto, trajetoDoAviso(org), "o terceiro só é avisado ao estacionar");
    }

    // C10 — regressão: repasse interno segue sem contato e sem aviso por canal
    @Test
    void repasse_interno_continua_sem_aviso() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        UUID dono = UUID.randomUUID();

        var r = sync(org, UUID.randomUUID(), repassar(pend, dono, null, null));

        assertEquals(Status.OK, r.get(0).status());
        assertEquals("DELEGADA", status(org, pend));
        assertEquals(dono, donoDaDelegacao(org, pend));
        assertNull(contatoDaDelegacao(org, pend));
        assertEquals(0L, countOutbox(org, "AVISO_REPASSE"), "interno recebe pela própria fila");
    }

    // C12 — RESOLVER com lembrete pelo comando (offline-first): fecha e agenda junto
    @Test
    void comando_resolver_com_lembrete_fecha_e_agenda() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        String quinta = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(3).toString();

        var r = sync(org, UUID.randomUUID(), resolverComLembrete(pend, quinta, "Reunião Sharpi"));

        assertEquals(Status.OK, r.get(0).status());
        assertEquals("FECHADA", status(org, pend));
        assertEquals(1L, countLembretes(org), "o compromisso virou item dormindo");
    }

    // C12 — data malformada é ERRO do comando, não silêncio: o compromisso não some
    @Test
    void comando_com_lembrete_invalido_e_recusado() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");

        var r = sync(org, UUID.randomUUID(), resolverComLembrete(pend, "quinta que vem", "Reunião"));

        assertEquals(Status.ERRO, r.get(0).status());
        assertEquals("ENTRADA", status(org, pend), "o item não fecha sem o lembrete");
        assertEquals(0L, countLembretes(org));
    }

    private static Comando resolverComLembrete(UUID pend, String quando, String texto) {
        return new Comando(UUID.randomUUID(), Intencao.RESOLVER, pend,
                new Campos(null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, new Comando.Lembrete(quando, texto)));
    }

    private long countLembretes(OrgId org) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT count(*) FROM pendencia WHERE org_id = ? AND origem = 'LEMBRETE'")
                .setParameter(1, org.valor()).getSingleResult())).longValue();
    }

    // ---- helpers -----------------------------------------------------------

    private List<ResultadoComando> sync(OrgId org, UUID pessoa, Comando c) {
        return comandos.sincronizar(org, pessoa, List.of(c));
    }

    private static Comando repassar(UUID pend, UUID dono, Contato contato, String aliasFalado) {
        return new Comando(UUID.randomUUID(), Intencao.REPASSAR, pend,
                campos(dono, contato, aliasFalado));
    }

    private static Campos campos(UUID dono, Contato contato, String aliasFalado) {
        return new Campos(dono, "N2", null, null, null, null, null, null, null, null, null,
                aliasFalado, null, contato, null);
    }

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    private UUID pendencia(OrgId org, String status) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status, valor_em_jogo)
                VALUES (?, ?, 'Protótipo de UX/UI', 'DECISAO', 'SEMANA', ?, 1000)
                """).setParameter(1, id).setParameter(2, org.valor()).setParameter(3, status)
                .executeUpdate());
        return id;
    }

    private String status(OrgId org, UUID pend) {
        return (String) unico("SELECT status FROM pendencia WHERE org_id = ? AND id = ?", org, pend);
    }

    private UUID donoDaDelegacao(OrgId org, UUID pend) {
        return (UUID) unico("SELECT dono_id FROM delegacao WHERE org_id = ? AND pendencia_id = ?",
                org, pend);
    }

    private UUID contatoDaDelegacao(OrgId org, UUID pend) {
        return (UUID) unico("SELECT contato_id FROM delegacao WHERE org_id = ? AND pendencia_id = ?",
                org, pend);
    }

    private long countDelegacoes(OrgId org, UUID pend) {
        return ((Number) unico(
                "SELECT count(*) FROM delegacao WHERE org_id = ? AND pendencia_id = ?", org, pend))
                .longValue();
    }

    private long countOutbox(OrgId org, String tipo) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT count(*) FROM outbox WHERE org_id = ? AND tipo = ?")
                .setParameter(1, org.valor()).setParameter(2, tipo).getSingleResult())).longValue();
    }

    private long countApelidos(OrgId org) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT count(*) FROM apelido_pessoa WHERE org_id = ?")
                .setParameter(1, org.valor()).getSingleResult())).longValue();
    }

    private UUID trajetoDoAviso(OrgId org) {
        UUID t = (UUID) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT trajeto_id FROM outbox WHERE org_id = ? AND tipo = 'AVISO_REPASSE'")
                .setParameter(1, org.valor()).getSingleResult());
        assertNotNull(t, "o aviso ficou represado no trajeto");
        return t;
    }

    private Object unico(String sql, OrgId org, Object segundo) {
        return QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(sql)
                .setParameter(1, org.valor()).setParameter(2, segundo).getSingleResult());
    }
}
