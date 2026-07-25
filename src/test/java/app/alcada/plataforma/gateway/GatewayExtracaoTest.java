package app.alcada.plataforma.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.gateway.internal.PoliticaProvedor;
import app.alcada.plataforma.gateway.internal.TransporteModelo.Status;
import app.alcada.plataforma.gateway.port.FalhasGateway;
import app.alcada.plataforma.gateway.port.ModelGateway;
import app.alcada.plataforma.gateway.port.Sensibilidade;
import app.alcada.plataforma.gateway.port.Tarefas.Extracao;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaExtracao;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Extração via gateway: schema estrito, indisponibilidade, política fixa e
 * observabilidade sem prompt/resposta (ADR-0020, RFC-0007).
 */
@QuarkusTest
class GatewayExtracaoTest {

    @Inject ModelGateway gateway;
    @Inject TransporteFake transporte;
    @Inject EntityManager em;

    private OrgId org;

    @BeforeEach
    void setup() {
        org = new OrgId(UUID.randomUUID());
        transporte.reset();
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome, sku) VALUES (?, ?, 'CLOUD')")
                        .setParameter(1, org.valor()).setParameter(2, "Org Cloud").executeUpdate());
    }

    @Test
    void provedor_sem_json_schema_falha_nunca_json_object() {
        transporte.programar(Status.SEM_SUPORTE_SCHEMA, null);
        assertThrows(FalhasGateway.ProvedorSemSchema.class, () -> extrair(UUID.randomUUID()));
    }

    @Test
    void indisponibilidade_nao_perde_a_captura() {
        UUID ref = UUID.randomUUID();
        transporte.programar(Status.INDISPONIVEL, null);

        Extracao<String> res = extrair(ref);

        assertNull(res.confianca(), "extração pendente: confianca = null");
        assertEquals(1L, contarReprocesso(ref), "tarefa enfileirada para reprocesso");
    }

    @Test
    void extracao_bem_sucedida_devolve_conteudo() {
        transporte.programar(Status.OK, "{\"titulo\":\"reembolso\"}");
        Extracao<String> res = extrair(UUID.randomUUID());
        assertEquals("{\"titulo\":\"reembolso\"}", res.valor());
    }

    @Test
    void politica_fixa_aplicada_e_nao_sobrescrita() {
        transporte.programar(Status.OK, "{}");
        extrair(UUID.randomUUID());

        PoliticaProvedor p = transporte.ultima().politica();
        assertFalse(p.allowFallbacks(), "allow_fallbacks:false");
        assertEquals("deny", p.dataCollection());
        assertTrue(p.zdr(), "zdr:true");
        assertTrue(p.requireParameters(), "require_parameters:true");
        assertEquals(java.util.List.of("deepinfra"), p.only(), "only = lista homologada do RIPD (Opção C)");
        assertTrue(transporte.ultima().schemaJson() != null, "extração leva schema estrito");
    }

    @Test
    void guardrail_recusa_provider_fora_da_lista_nao_degrada() {
        transporte.programar(app.alcada.plataforma.gateway.internal.TransporteModelo.Status.GUARDRAIL_RECUSOU, null);
        // roteamento fora do `only` vira erro tratado — nunca degrada nem enfileira como pendente
        assertThrows(app.alcada.plataforma.gateway.port.FalhasGateway.GuardrailRecusou.class,
                () -> extrair(UUID.randomUUID()));
    }

    @Test
    void log_nao_contem_prompt_nem_resposta() {
        // A tabela sequer tem colunas para prompt/resposta.
        @SuppressWarnings("unchecked")
        List<String> colunas = em.createNativeQuery(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'chamada_modelo'")
                .getResultList();
        assertFalse(colunas.contains("prompt"));
        assertFalse(colunas.contains("resposta"));

        // E o registro guarda a referência, não o conteúdo.
        UUID ref = UUID.randomUUID();
        transporte.programar(Status.OK, "{}");
        extrair(ref);
        Object refStored = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT ref_mensagem_id FROM chamada_modelo WHERE org_id = ? ORDER BY ocorrido_em DESC LIMIT 1")
                .setParameter(1, org.valor()).getSingleResult());
        assertEquals(ref, refStored);
    }

    private Extracao<String> extrair(UUID ref) {
        return gateway.extrair(new TarefaExtracao<>(
                org, Sensibilidade.INTERNA, ref, "texto minimizado", "{\"type\":\"object\"}", s -> s));
    }

    private long contarReprocesso(UUID ref) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT count(*) FROM tarefa_reprocesso WHERE org_id = ? AND ref_mensagem_id = ?")
                .setParameter(1, org.valor()).setParameter(2, ref).getSingleResult())).longValue();
    }
}
