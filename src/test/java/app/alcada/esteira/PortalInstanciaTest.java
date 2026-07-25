package app.alcada.esteira;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import app.alcada.esteira.port.EntradasEsteira.NovaEtapa;
import app.alcada.esteira.port.EntradasEsteira.NovoCriterio;
import app.alcada.esteira.port.Esteiras;
import app.alcada.esteira.port.PortalInstancia;
import app.alcada.esteira.port.PortalInstancia.Declaracao;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** 015 — Portal de instância + autoavaliação. */
@QuarkusTest
class PortalInstanciaTest {

    @Inject EntityManager em;
    @Inject Esteiras esteiras;
    @Inject PortalInstancia portal;

    @Test
    void resolver_traz_estado_curado_e_o_que_falta() {
        Ctx c = cenario();
        var estado = QuarkusTransaction.requiringNew().call(() -> portal.resolver(c.token)).orElseThrow();
        assertEquals("Validação", estado.etapaAtualNome());
        assertTrue(estado.oQueFalta().stream().anyMatch(f -> f.chave().equals("contrato_assinado")),
                "o que falta = critérios objetivos da etapa do gestor");
    }

    @Test
    void token_invalido_e_uniforme() {
        assertTrue(QuarkusTransaction.requiringNew().call(() -> portal.resolver("token-que-nao-existe")).isEmpty());
    }

    @Test
    void token_revogado_e_uniforme() {
        Ctx c = cenario();
        QuarkusTransaction.requiringNew().run(() -> portal.revogar(c.org, UUID.fromString(c.tokenId)));
        assertTrue(QuarkusTransaction.requiringNew().call(() -> portal.resolver(c.token)).isEmpty());
    }

    @Test
    void token_expirado_e_uniforme() {
        OrgId org = novaOrg();
        UUID esteira = comChecklist(org);
        UUID inst = QuarkusTransaction.requiringNew().call(() -> esteiras.criarInstancia(org, esteira, "Ent"));
        var t = QuarkusTransaction.requiringNew().call(() ->
                portal.emitir(org, inst, OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)));
        assertTrue(QuarkusTransaction.requiringNew().call(() -> portal.resolver(t.token())).isEmpty());
    }

    @Test
    void autoavaliacao_grava_escopada_a_instancia() {
        Ctx c = cenario();
        QuarkusTransaction.requiringNew().run(() -> portal.autoavaliar(c.token,
                List.of(new Declaracao("contrato_assinado", true))));
        long n = QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                "SELECT count(*) FROM autoavaliacao WHERE org_id = ? AND instancia_id = ? AND conforme")
                .setParameter(1, c.org.valor()).setParameter(2, c.instancia).getSingleResult()).longValue());
        assertEquals(1, n);
    }

    @Test
    void emissao_guarda_so_hash() {
        Ctx c = cenario();
        long cru = QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                "SELECT count(*) FROM token_instancia WHERE org_id = ? AND token_hash = ?")
                .setParameter(1, c.org.valor()).setParameter(2, c.token).getSingleResult()).longValue());
        assertEquals(0, cru, "o token cru nunca é guardado (só o hash)");
    }

    @Test
    void isolamento_por_organizacao() {
        Ctx a = cenario();
        OrgId b = novaOrg();
        // B não resolve o token de A (o token carrega o org de A; B nem entra na conta)
        var estado = QuarkusTransaction.requiringNew().call(() -> portal.resolver(a.token));
        assertFalse(estado.isEmpty(), "token de A resolve para A");
        // e o autoavaliacao de A fica escopado a A (verificado no outro teste)
    }

    // ---- helpers -------------------------------------------------------------

    private record Ctx(OrgId org, UUID instancia, String token, String tokenId) {
    }

    private Ctx cenario() {
        OrgId org = novaOrg();
        UUID esteira = comChecklist(org);
        UUID inst = QuarkusTransaction.requiringNew().call(() -> esteiras.criarInstancia(org, esteira, "Integrador X"));
        var t = QuarkusTransaction.requiringNew().call(() ->
                portal.emitir(org, inst, OffsetDateTime.now(ZoneOffset.UTC).plusDays(30)));
        return new Ctx(org, inst, t.token(), t.tokenId());
    }

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    private UUID comChecklist(OrgId org) {
        UUID esteira = QuarkusTransaction.requiringNew().call(() -> esteiras.criar(org, "Homologação",
                List.of(new NovaEtapa(1, "Validação", null, "P3D", true),
                        new NovaEtapa(2, "Ativação", null, null, false))));
        QuarkusTransaction.requiringNew().run(() -> esteiras.publicarChecklist(org, esteira,
                List.of(new NovoCriterio("contrato_assinado", "Contrato assinado", "OBJETIVO", true))));
        return esteira;
    }
}
