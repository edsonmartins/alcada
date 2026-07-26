package app.alcada.consulta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import app.alcada.consulta.internal.ConsultaJdbc;
import app.alcada.consulta.port.ResultadoConsulta;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** Cenários do 020 (consulta em linguagem natural, RFC-0004 §3). */
@QuarkusTest
class ConsultaBackendTest {

    @Inject ConsultaJdbc consulta;
    @Inject EntityManager em;

    // C1 — "quanto está parado esperando por mim"
    @Test
    void esperando_por_mim_conta_e_soma_os_itens_em_entrada() {
        OrgId org = new OrgId(UUID.randomUUID());
        novaEntrada(org, "Aprovar reembolso", "DECISAO", new BigDecimal("1000"), null, 0);
        novaEntrada(org, "Validar proposta", "DECISAO", new BigDecimal("2000"), null, 0);

        ResultadoConsulta r = consulta.consultar(org, "quanto está parado esperando por mim");

        assertEquals("ESPERANDO_MIM", r.template());
        assertEquals(2, r.itens().size());
        assertTrue(r.resposta().contains("2"), "conta os dois itens: " + r.resposta());
        assertTrue(r.resposta().toLowerCase().contains("mil") || r.resposta().contains("3000"),
                "soma o valor: " + r.resposta());
    }

    // C2 — "o que trava por causa do financeiro"
    @Test
    void travado_por_area_filtra_bloqueios_pela_area() {
        OrgId org = new OrgId(UUID.randomUUID());
        novaBloqueio(org, "Liberar orçamento", "Financeiro", "aprovação do financeiro");
        novaBloqueio(org, "Acesso ao sistema", "TI", "senha do TI");

        ResultadoConsulta r = consulta.consultar(org, "o que trava por causa do financeiro");

        assertEquals("TRAVADO_POR", r.template());
        assertEquals(1, r.itens().size(), "só o bloqueio do financeiro");
        assertEquals("Liberar orçamento", r.itens().get(0).titulo());
    }

    // C3 — "o que estou empurrando com a barriga"
    @Test
    void aversivos_lista_itens_adiados_tres_vezes_ou_mais() {
        OrgId org = new OrgId(UUID.randomUUID());
        novaEntrada(org, "Item adiado", "DECISAO", null, null, 3);
        novaEntrada(org, "Item novo", "DECISAO", null, null, 0);

        ResultadoConsulta r = consulta.consultar(org, "o que estou empurrando com a barriga");

        assertEquals("AVERSIVOS", r.template());
        assertEquals(1, r.itens().size());
        assertEquals("Item adiado", r.itens().get(0).titulo());
    }

    // C4 — pergunta fora do escopo da fila
    @Test
    void pergunta_fora_do_escopo_nao_inventa() {
        OrgId org = new OrgId(UUID.randomUUID());
        novaEntrada(org, "Algum item", "DECISAO", null, null, 0);

        ResultadoConsulta r = consulta.consultar(org, "qual a previsão do tempo amanhã");

        assertEquals("DESCONHECIDO", r.template());
        assertTrue(r.itens().isEmpty(), "não devolve itens");
        assertTrue(r.resposta().toLowerCase().contains("não sei")
                || r.resposta().toLowerCase().contains("nao sei"), r.resposta());
    }

    // C5 — isolamento por organização (INV-15)
    @Test
    void consulta_nao_enxerga_itens_de_outra_organizacao() {
        OrgId minha = new OrgId(UUID.randomUUID());
        OrgId outra = new OrgId(UUID.randomUUID());
        novaEntrada(minha, "Meu item", "DECISAO", new BigDecimal("500"), null, 0);
        novaEntrada(outra, "Item alheio", "DECISAO", new BigDecimal("999"), null, 0);

        ResultadoConsulta r = consulta.consultar(minha, "quanto está esperando por mim");

        assertEquals(1, r.itens().size());
        assertEquals("Meu item", r.itens().get(0).titulo());
        assertFalse(r.resposta().contains("999"), "não vaza valor da outra org");
    }

    // ---- helpers -----------------------------------------------------------

    private void novaEntrada(OrgId org, String titulo, String classe, BigDecimal valor,
                             String quemEspera, int adiado) {
        inserir(org, titulo, classe, "ENTRADA", valor, quemEspera, null, adiado);
    }

    private void novaBloqueio(OrgId org, String titulo, String quemEspera, String oQueTrava) {
        inserir(org, titulo, "BLOQUEIO", "ENTRADA", null, quemEspera, oQueTrava, 0);
    }

    private void inserir(OrgId org, String titulo, String classe, String status, BigDecimal valor,
                         String quemEspera, String oQueTrava, int adiado) {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org') ON CONFLICT DO NOTHING")
                    .setParameter(1, org.valor()).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO pendencia (id, org_id, titulo, classe, horizonte, status,
                                           valor_em_jogo, quem_espera, o_que_trava, adiado_count)
                    VALUES (?, ?, ?, ?, 'SEMANA', ?, ?, ?, ?, ?)
                    """)
                    .setParameter(1, UUID.randomUUID()).setParameter(2, org.valor())
                    .setParameter(3, titulo).setParameter(4, classe).setParameter(5, status)
                    .setParameter(6, valor).setParameter(7, quemEspera).setParameter(8, oQueTrava)
                    .setParameter(9, adiado).executeUpdate();
        });
    }
}
