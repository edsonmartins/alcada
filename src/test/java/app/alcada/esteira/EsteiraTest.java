package app.alcada.esteira;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import app.alcada.esteira.port.AvaliacaoResultado;
import app.alcada.esteira.port.Avaliacoes;
import app.alcada.esteira.port.EntradasEsteira.ApontamentoItem;
import app.alcada.esteira.port.EntradasEsteira.NovaEtapa;
import app.alcada.esteira.port.EntradasEsteira.NovoCriterio;
import app.alcada.esteira.port.EntradasEsteira.ResultadoItem;
import app.alcada.esteira.port.Esteiras;
import app.alcada.esteira.port.MineracaoChecklist;
import app.alcada.esteira.port.PropostaChecklist;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** 012 — Esteira, checklist versionado e mineração §B (min-reprovacoes=2 no teste). */
@QuarkusTest
class EsteiraTest {

    @Inject EntityManager em;
    @Inject Esteiras esteiras;
    @Inject Avaliacoes avaliacoes;
    @Inject MineracaoChecklist mineracao;

    @Test
    void aprovada_avanca_sem_pendencia() {
        OrgId org = novaOrg();
        UUID esteira = comChecklist(org, new NovoCriterio("doc_ok", "Documento em ordem", "OBJETIVO", true));
        UUID inst = QuarkusTransaction.requiringNew().call(() -> esteiras.criarInstancia(org, esteira, "Integrador X"));
        AvaliacaoResultado r = QuarkusTransaction.requiringNew().call(() ->
                avaliacoes.avaliar(org, inst, List.of(new ResultadoItem("doc_ok", "OK")), List.of(), null));
        assertEquals("APROVADA", r.desfecho());
        assertNull(r.pendenciaId(), "aprovada não gera pendência");
        assertEquals(0L, pendenciasEsteira(org), "nenhuma pendência ESTEIRA");
    }

    @Test
    void falha_objetiva_gera_pendencia_anexada() {
        OrgId org = novaOrg();
        UUID esteira = comChecklist(org, new NovoCriterio("doc_ok", "Documento em ordem", "OBJETIVO", true));
        UUID inst = QuarkusTransaction.requiringNew().call(() -> esteiras.criarInstancia(org, esteira, "Integrador X"));
        AvaliacaoResultado r = QuarkusTransaction.requiringNew().call(() ->
                avaliacoes.avaliar(org, inst, List.of(new ResultadoItem("doc_ok", "FALHOU")), List.of(), null));
        assertEquals("REPROVADA", r.desfecho());
        assertFalse(r.pendenciaId() == null, "gera pendência");
        assertEquals(1L, pendenciasEsteira(org));
        assertEquals(1L, trilhaCaptada(org, UUID.fromString(r.pendenciaId())));
    }

    @Test
    void julgamento_pendente_gera_pendencia() {
        OrgId org = novaOrg();
        UUID esteira = comChecklist(org, new NovoCriterio("risco", "Avaliar risco", "JULGAMENTO", true));
        UUID inst = QuarkusTransaction.requiringNew().call(() -> esteiras.criarInstancia(org, esteira, "Parceiro Y"));
        AvaliacaoResultado r = QuarkusTransaction.requiringNew().call(() ->
                avaliacoes.avaliar(org, inst, List.of(), List.of(), null));
        assertEquals("PENDENTE_JULGAMENTO", r.desfecho());
        assertEquals(1L, pendenciasEsteira(org));
    }

    @Test
    void checklist_versionado_nunca_sobrescreve() {
        OrgId org = novaOrg();
        UUID esteira = comChecklist(org, new NovoCriterio("a", "A", "OBJETIVO", true));
        int v = QuarkusTransaction.requiringNew().call(() ->
                esteiras.publicarChecklist(org, esteira, List.of(new NovoCriterio("b", "B", "OBJETIVO", true))));
        assertEquals(3, v, "v1 inicial + v2 (comChecklist) + v3 aqui");
        assertEquals(3, QuarkusTransaction.requiringNew().call(() -> esteiras.checklistVigente(org, esteira).versao()));
    }

    @Test
    void mineracao_propoe_objetivo_recorrente_e_separa_julgamento() {
        OrgId org = novaOrg();
        UUID esteira = comChecklist(org, new NovoCriterio("doc_ok", "Documento em ordem", "OBJETIVO", true));
        // duas reprovações, ambas apontam "campos fiscais" (objetivo) e uma aponta julgamento
        reprovar(org, esteira, List.of(new ApontamentoItem("campos fiscais", "OBJETIVO")));
        reprovar(org, esteira, List.of(new ApontamentoItem("campos fiscais", "OBJETIVO"),
                new ApontamentoItem("fit cultural", "JULGAMENTO")));
        PropostaChecklist p = QuarkusTransaction.requiringNew().call(() -> mineracao.propostas(org, esteira));
        assertTrue(p.objetivos().stream().anyMatch(c -> c.descricao().equals("campos fiscais")),
                "objetivo em 100% das reprovações vira candidato");
        assertTrue(p.julgamento().contains("fit cultural"), "julgamento à parte (não vira checklist)");
    }

    @Test
    void poucas_reprovacoes_nao_propoem() {
        OrgId org = novaOrg();
        UUID esteira = comChecklist(org, new NovoCriterio("doc_ok", "Documento em ordem", "OBJETIVO", true));
        reprovar(org, esteira, List.of(new ApontamentoItem("campos fiscais", "OBJETIVO"))); // só 1 (< min 2)
        PropostaChecklist p = QuarkusTransaction.requiringNew().call(() -> mineracao.propostas(org, esteira));
        assertTrue(p.objetivos().isEmpty(), "abaixo do mínimo não propõe");
    }

    @Test
    void isolamento_por_organizacao() {
        OrgId a = novaOrg();
        OrgId b = novaOrg();
        UUID esteiraA = comChecklist(a, new NovoCriterio("doc_ok", "Documento em ordem", "OBJETIVO", true));
        reprovar(a, esteiraA, List.of(new ApontamentoItem("campos fiscais", "OBJETIVO")));
        reprovar(a, esteiraA, List.of(new ApontamentoItem("campos fiscais", "OBJETIVO")));
        UUID esteiraB = comChecklist(b, new NovoCriterio("doc_ok", "Documento em ordem", "OBJETIVO", true));
        PropostaChecklist pb = QuarkusTransaction.requiringNew().call(() -> mineracao.propostas(b, esteiraB));
        assertTrue(pb.objetivos().isEmpty(), "a mineração de B não vê reprovações de A");
    }

    // ---- helpers -------------------------------------------------------------

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    /** Esteira com etapa do gestor (ordem 1) + etapa final (ordem 2), e checklist v2 com o critério. */
    private UUID comChecklist(OrgId org, NovoCriterio criterio) {
        UUID esteira = QuarkusTransaction.requiringNew().call(() -> esteiras.criar(org, "Homologação",
                List.of(new NovaEtapa(1, "Validação", null, "P3D", true),
                        new NovaEtapa(2, "Ativação", null, null, false))));
        QuarkusTransaction.requiringNew().run(() -> esteiras.publicarChecklist(org, esteira, List.of(criterio)));
        return esteira;
    }

    private void reprovar(OrgId org, UUID esteira, List<ApontamentoItem> apontamentos) {
        UUID inst = QuarkusTransaction.requiringNew().call(() -> esteiras.criarInstancia(org, esteira, "Ent " + UUID.randomUUID()));
        QuarkusTransaction.requiringNew().run(() ->
                avaliacoes.avaliar(org, inst, List.of(new ResultadoItem("doc_ok", "FALHOU")), apontamentos, null));
    }

    private long pendenciasEsteira(OrgId org) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                "SELECT count(*) FROM pendencia WHERE org_id = ? AND classe = 'ESTEIRA'")
                .setParameter(1, org.valor()).getSingleResult()).longValue());
    }

    private long trilhaCaptada(OrgId org, UUID pendenciaId) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                "SELECT count(*) FROM trilha WHERE org_id = ? AND pendencia_id = ? AND tipo = 'CAPTADA'")
                .setParameter(1, org.valor()).setParameter(2, pendenciaId).getSingleResult()).longValue());
    }
}
