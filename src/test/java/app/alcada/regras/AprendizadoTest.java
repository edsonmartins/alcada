package app.alcada.regras;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import app.alcada.regras.port.Aprendizado;
import app.alcada.regras.port.Mineracao;
import app.alcada.regras.port.PerguntaAprendizado;
import app.alcada.regras.port.Regras;
import app.alcada.regras.port.Resposta;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** 011 — Laço de aprendizado (min-ocorrencias=3 no profile de teste). */
@QuarkusTest
class AprendizadoTest {

    @Inject EntityManager em;
    @Inject Trilha trilha;
    @Inject Aprendizado aprendizado;
    @Inject Mineracao mineracao;
    @Inject Regras regras;

    @Test
    void candidata_gera_pergunta_com_evidencia() {
        OrgId org = novaOrg();
        semear(org, "DECISAO", 3);
        PerguntaAprendizado p = umaAberta(org);
        assertEquals("DECISAO", p.classe());
        assertFalse(p.casos().isEmpty(), "evidência clicável (ADR-0019)");
        assertEquals(1L, contar(org, "SUGESTAO_EMITIDA"), "registra SUGESTAO_EMITIDA");
    }

    @Test
    void uma_pergunta_aberta_por_classe() {
        OrgId org = novaOrg();
        semear(org, "DECISAO", 3);
        QuarkusTransaction.requiringNew().call(() -> aprendizado.perguntasAbertas(org));
        var segunda = QuarkusTransaction.requiringNew().call(() -> aprendizado.perguntasAbertas(org));
        assertEquals(1, segunda.stream().filter(q -> q.classe().equals("DECISAO")).count(),
                "não cria segunda pergunta aberta para a mesma classe");
    }

    @Test
    void sim_cria_a_regra() {
        OrgId org = novaOrg();
        semear(org, "DECISAO", 3);
        PerguntaAprendizado p = umaAberta(org);
        QuarkusTransaction.requiringNew().run(() ->
                aprendizado.responder(org, UUID.fromString(p.id()), Resposta.SIM, UUID.randomUUID()));
        assertTrue(QuarkusTransaction.requiringNew().call(() -> regras.existeRegraAtiva(org, "DECISAO")),
                "sim cria a regra (INV-10: o sim é a confirmação humana)");
        assertEquals(1L, contar(org, "SUGESTAO_ACEITA"));
    }

    @Test
    void agora_nao_recusa_sem_silenciar_e_nao_repergunta_na_semana() {
        OrgId org = novaOrg();
        semear(org, "DECISAO", 3);
        PerguntaAprendizado p = umaAberta(org);
        QuarkusTransaction.requiringNew().run(() ->
                aprendizado.responder(org, UUID.fromString(p.id()), Resposta.AGORA_NAO, null));
        assertEquals(1L, contar(org, "SUGESTAO_RECUSADA"));
        var abertas = QuarkusTransaction.requiringNew().call(() -> aprendizado.perguntasAbertas(org));
        assertTrue(abertas.stream().noneMatch(q -> q.classe().equals("DECISAO")), "não re-pergunta na semana");
        // mas a proposta continua no 010 (não foi silenciada)
        assertTrue(QuarkusTransaction.requiringNew().call(() -> mineracao.propostas(org))
                .stream().anyMatch(pr -> pr.classe().equals("DECISAO")), "agora não ≠ silenciar");
    }

    @Test
    void nao_perguntar_silencia_a_classe() {
        OrgId org = novaOrg();
        semear(org, "DECISAO", 3);
        PerguntaAprendizado p = umaAberta(org);
        QuarkusTransaction.requiringNew().run(() ->
                aprendizado.responder(org, UUID.fromString(p.id()), Resposta.NAO_PERGUNTAR, null));
        assertEquals(1L, contar(org, "SUGESTAO_SILENCIADA"));
        assertTrue(QuarkusTransaction.requiringNew().call(() -> mineracao.propostas(org)).isEmpty(),
                "classe silenciada some das propostas");
        var abertas = QuarkusTransaction.requiringNew().call(() -> aprendizado.perguntasAbertas(org));
        assertTrue(abertas.isEmpty(), "e das perguntas");
    }

    @Test
    void sem_evidencia_nao_pergunta() {
        OrgId org = novaOrg();
        semear(org, "DECISAO", 2); // abaixo do mínimo (3)
        var abertas = QuarkusTransaction.requiringNew().call(() -> aprendizado.perguntasAbertas(org));
        assertTrue(abertas.isEmpty(), "sem candidata não há pergunta (ADR-0019)");
    }

    @Test
    void isolamento_por_organizacao() {
        OrgId a = novaOrg();
        OrgId b = novaOrg();
        semear(a, "DECISAO", 3);
        var abertasB = QuarkusTransaction.requiringNew().call(() -> aprendizado.perguntasAbertas(b));
        assertTrue(abertasB.isEmpty(), "as perguntas de B não veem casos de A");
    }

    // ---- helpers -------------------------------------------------------------

    private PerguntaAprendizado umaAberta(OrgId org) {
        List<PerguntaAprendizado> abertas = QuarkusTransaction.requiringNew().call(() -> aprendizado.perguntasAbertas(org));
        return abertas.stream().filter(q -> q.classe().equals("DECISAO")).findFirst().orElseThrow();
    }

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    private void semear(OrgId org, String classe, int n) {
        for (int i = 0; i < n; i++) {
            UUID id = UUID.randomUUID();
            QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                    INSERT INTO pendencia (id, org_id, titulo, status, classe, horizonte)
                    VALUES (?, ?, ?, 'FECHADA', ?, 'SEMANA')
                    """).setParameter(1, id).setParameter(2, org.valor())
                    .setParameter(3, "Item " + id).setParameter(4, classe).executeUpdate());
            QuarkusTransaction.requiringNew().run(() -> trilha.registrar(new EventoTrilha(
                    org, id, TipoEvento.RESOLVIDA, Ator.sistemaMotor("teste"), null, null, null, null)));
        }
    }

    private long contar(OrgId org, String tipo) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                "SELECT count(*) FROM trilha WHERE org_id = ? AND tipo = ?")
                .setParameter(1, org.valor()).setParameter(2, tipo).getSingleResult()).longValue());
    }
}
