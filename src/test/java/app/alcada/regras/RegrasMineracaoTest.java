package app.alcada.regras;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.regras.port.Mineracao;
import app.alcada.regras.port.PropostaRegra;
import app.alcada.regras.port.Regras;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** 010 — Mineração de regra de autonomia (min-ocorrencias=3 no profile de teste). */
@QuarkusTest
class RegrasMineracaoTest {

    @Inject EntityManager em;
    @Inject Trilha trilha;
    @Inject Mineracao mineracao;
    @Inject Regras regras;

    @Test
    void classe_consistente_vira_proposta_com_evidencia() {
        OrgId org = novaOrg();
        for (int i = 0; i < 3; i++) {
            decisaoResolvida(org, "Reembolso " + i);
        }
        PropostaRegra p = QuarkusTransaction.requiringNew().call(() -> uma(mineracao.propostas(org), "DECISAO"));
        assertEquals(3, p.ocorrencias());
        assertEquals(1.0, p.consistencia(), 0.001);
        assertFalse(p.casos().isEmpty(), "evidência clicável (ADR-0019)");
    }

    @Test
    void poucos_casos_nao_viram_regra() {
        OrgId org = novaOrg();
        decisaoResolvida(org, "Só um");
        decisaoResolvida(org, "Só dois");
        var props = QuarkusTransaction.requiringNew().call(() -> mineracao.propostas(org));
        assertTrue(props.stream().noneMatch(p -> p.classe().equals("DECISAO")), "abaixo do mínimo (3)");
    }

    @Test
    void reversao_derruba_a_candidata() {
        OrgId org = novaOrg();
        decisaoResolvida(org, "A");
        decisaoResolvida(org, "B");
        UUID c = decisaoResolvida(org, "C");
        registrar(org, c, TipoEvento.ESCALADA); // uma reversão basta
        var props = QuarkusTransaction.requiringNew().call(() -> mineracao.propostas(org));
        assertTrue(props.stream().noneMatch(p -> p.classe().equals("DECISAO")), "zero reversões é condição dura");
    }

    @Test
    void aceitar_cria_regra_e_some_das_propostas() {
        OrgId org = novaOrg();
        for (int i = 0; i < 3; i++) {
            decisaoResolvida(org, "Item " + i);
        }
        UUID dono = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> regras.criar(org, "DECISAO", "N1", dono));

        boolean ativa = QuarkusTransaction.requiringNew().call(() -> regras.existeRegraAtiva(org, "DECISAO"));
        assertTrue(ativa, "regra criada e ativa (motor de captura já a consome)");
        var props = QuarkusTransaction.requiringNew().call(() -> mineracao.propostas(org));
        assertTrue(props.stream().noneMatch(p -> p.classe().equals("DECISAO")), "some das propostas com regra ativa");
    }

    @Test
    void silenciar_remove_a_proposta() {
        OrgId org = novaOrg();
        for (int i = 0; i < 3; i++) {
            decisaoResolvida(org, "Item " + i);
        }
        QuarkusTransaction.requiringNew().run(() -> regras.silenciar(org, "DECISAO", null));
        var props = QuarkusTransaction.requiringNew().call(() -> mineracao.propostas(org));
        assertTrue(props.stream().noneMatch(p -> p.classe().equals("DECISAO")), "classe silenciada não é proposta");
    }

    @Test
    void aceitar_via_api_recusa_segunda_regra_e_nivel_acima_do_maximo() {
        OrgId org = novaOrg();
        // classe_decisao com teto N2 → aceitar N1 deve dar 422
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO classe_decisao (org_id, classe, nivel_maximo) VALUES (?, 'DECISAO', 'N2')
                """).setParameter(1, org.valor()).executeUpdate());
        String dono = UUID.randomUUID().toString();

        given().header("X-Org-Id", org.valor().toString()).contentType("application/json")
                .body("{\"classe\":\"DECISAO\",\"nivel\":\"N1\",\"donoId\":\"" + dono + "\"}")
        .when().post("/v1/regras").then().statusCode(422);

        // N2 é permitido (== teto) → 201
        given().header("X-Org-Id", org.valor().toString()).contentType("application/json")
                .body("{\"classe\":\"DECISAO\",\"nivel\":\"N2\",\"donoId\":\"" + dono + "\"}")
        .when().post("/v1/regras").then().statusCode(201);

        // segunda regra para a mesma classe → 409
        given().header("X-Org-Id", org.valor().toString()).contentType("application/json")
                .body("{\"classe\":\"DECISAO\",\"nivel\":\"N2\",\"donoId\":\"" + dono + "\"}")
        .when().post("/v1/regras").then().statusCode(409);
    }

    @Test
    void isolamento_por_organizacao() {
        OrgId a = novaOrg();
        OrgId b = novaOrg();
        for (int i = 0; i < 3; i++) {
            decisaoResolvida(a, "A" + i);
        }
        var propsB = QuarkusTransaction.requiringNew().call(() -> mineracao.propostas(b));
        assertTrue(propsB.isEmpty(), "a mineração de B nunca vê casos de A");
    }

    // ---- helpers -------------------------------------------------------------

    private static PropostaRegra uma(List<PropostaRegra> props, String classe) {
        return props.stream().filter(p -> p.classe().equals(classe)).findFirst().orElseThrow();
    }

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    /** Cria pendência DECISAO fechada + trilha RESOLVIDA. Retorna o id. */
    private UUID decisaoResolvida(OrgId org, String titulo) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO pendencia (id, org_id, titulo, status, classe, horizonte)
                VALUES (?, ?, ?, 'FECHADA', 'DECISAO', 'SEMANA')
                """).setParameter(1, id).setParameter(2, org.valor()).setParameter(3, titulo).executeUpdate());
        registrar(org, id, TipoEvento.RESOLVIDA);
        return id;
    }

    private void registrar(OrgId org, UUID pendencia, TipoEvento tipo) {
        QuarkusTransaction.requiringNew().run(() -> trilha.registrar(new EventoTrilha(
                org, pendencia, tipo, Ator.sistemaMotor("teste"), null, null, null, null)));
    }
}
