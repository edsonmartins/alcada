package app.alcada.metricas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import app.alcada.metricas.port.Radar;
import app.alcada.metricas.port.RadarDados;
import app.alcada.metricas.port.RevisaoDados;
import app.alcada.metricas.port.RevisaoSemanal;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** 009 — Radar e Revisão de sexta. Leitura pura sobre pendência/delegação/trilha. */
@QuarkusTest
class RadarRevisaoTest {

    @Inject EntityManager em;
    @Inject Trilha trilha;
    @Inject Radar radar;
    @Inject RevisaoSemanal revisao;

    @Test
    void radar_conta_dependencia_e_o_que_roda_sem_voce() {
        OrgId org = novaOrg();
        pendencia(org, "Na entrada", "ENTRADA", 0);
        pendencia(org, "Agendada", "AGENDADA", 0);
        UUID pN3 = pendencia(org, "Delegada N3", "DELEGADA", 0);
        delegacao(org, pN3, "N3", "ABERTA");
        UUID pN2 = pendencia(org, "Delegada N2", "DELEGADA", 0);
        delegacao(org, pN2, "N2", "PROPOSTA");

        RadarDados r = QuarkusTransaction.requiringNew().call(() -> radar.calcular(org));
        // dependem: entrada + agendada + delegada N3 = 3, de 4 abertos
        assertEquals(3, r.dependeDoGestor().qtd());
        assertEquals(4, r.dependeDoGestor().total());
        assertEquals(75, r.dependeDoGestor().pct());
        // roda sem você: só a N2 ativa
        assertEquals(1, r.rodandoSemVoce());
    }

    @Test
    void radar_lista_adiados_tres_vezes_ou_mais() {
        OrgId org = novaOrg();
        pendencia(org, "Adiado 3x", "ENTRADA", 3);
        pendencia(org, "Adiado 5x", "ENTRADA", 5);
        pendencia(org, "Adiado 1x", "ENTRADA", 1);

        RadarDados r = QuarkusTransaction.requiringNew().call(() -> radar.calcular(org));
        assertEquals(2, r.adiados().size(), "só os com adiado_count >= 3");
        assertEquals("Adiado 5x", r.adiados().get(0).titulo(), "ordenado por contador desc");
    }

    @Test
    void radar_conta_autonomia_separadamente() {
        OrgId org = novaOrg();
        UUID p = pendencia(org, "Item", "FECHADA", 0);
        registrar(org, p, TipoEvento.EXECUTADA);
        registrar(org, p, TipoEvento.EXECUTADA_POR_AUSENCIA);
        registrar(org, p, TipoEvento.EXECUTADA_POR_AUSENCIA);
        registrar(org, p, TipoEvento.DEVOLVIDA_PELO_EXECUTOR);
        registrar(org, p, TipoEvento.ESCALADA);

        RadarDados.Autonomia a = QuarkusTransaction.requiringNew().call(() -> radar.calcular(org)).autonomia();
        assertEquals(1, a.deliberada());
        assertEquals(2, a.porAusencia(), "ausência NUNCA somada à deliberada (ADR-0024)");
        assertEquals(1, a.devolvida());
        assertEquals(1, a.escalada());
    }

    @Test
    void radar_conta_fechamento_no_canal_separadamente() {
        OrgId org = novaOrg();
        UUID p = pendencia(org, "Item", "FECHADA", 0);
        registrar(org, p, TipoEvento.COMUNICADA);
        registrar(org, p, TipoEvento.FALHA_COMUNICACAO);
        registrar(org, p, TipoEvento.COMUNICACAO_IMPOSSIVEL);

        RadarDados.FechamentoCanal f =
                QuarkusTransaction.requiringNew().call(() -> radar.calcular(org)).fechamentoCanal();
        assertEquals(1, f.entregue());
        assertEquals(1, f.falho());
        assertEquals(1, f.impossivel());
    }

    @Test
    void radar_serie_de_oito_semanas_sem_buracos() {
        OrgId org = novaOrg();
        UUID p = pendencia(org, "Item", "ENTRADA", 0);
        registrar(org, p, TipoEvento.CAPTADA);

        var serie = QuarkusTransaction.requiringNew().call(() -> radar.calcular(org)).encolhimento();
        assertEquals(8, serie.size(), "sempre 8 buckets");
        // a semana corrente (última) tem ao menos a CAPTADA registrada agora
        assertTrue(serie.get(7).entraram() >= 1);
    }

    @Test
    void revisao_traz_entrada_dica_e_resumo() {
        OrgId org = novaOrg();
        pendencia(org, "Entrada A", "ENTRADA", 0);
        pendencia(org, "Entrada B", "ENTRADA", 0);
        // 3 decisões da classe DECISAO resolvidas → vira dica
        for (int i = 0; i < 3; i++) {
            UUID p = pendencia(org, "Resolvida " + i, "FECHADA", 0);
            registrar(org, p, TipoEvento.RESOLVIDA);
        }

        RevisaoDados rv = QuarkusTransaction.requiringNew().call(() -> revisao.calcular(org));
        assertEquals(2, rv.entrada().qtd());
        assertEquals(3, rv.resumoSemana().resolvidas());
        assertTrue(rv.podeVirarRegra().stream().anyMatch(d -> d.classe().equals("DECISAO") && d.ocorrencias() >= 3),
                "assinatura {DECISAO} com >=3 resolvidas vira dica");
    }

    // 018/009 — saúde do gateway exposta no radar: chamadas, falhas e custo.
    @Test
    void radar_expoe_saude_do_gateway() {
        OrgId org = novaOrg();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("""
                    INSERT INTO chamada_modelo (org_id, tarefa, sensibilidade, destino, custo, schema_ok)
                    VALUES (?, 'extracao', 'INTERNA', 'EXTERNO', 0.002, true)
                    """).setParameter(1, org.valor()).executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO chamada_modelo (org_id, tarefa, sensibilidade, destino, custo, schema_ok)
                    VALUES (?, 'extracao', 'INTERNA', 'EXTERNO', 0, false)
                    """).setParameter(1, org.valor()).executeUpdate();
        });

        RadarDados.SaudeGateway s = QuarkusTransaction.requiringNew().call(() -> radar.calcular(org)).saudeGateway();
        assertEquals(2, s.chamadas());
        assertEquals(1, s.falhas(), "a chamada com schema_ok=false conta como falha");
        assertTrue(s.custo() > 0, "soma o custo");
    }

    // RFC-0004 §4 — condução: uma frase-guia por passo, coerente com os números.
    @Test
    void revisao_conduz_cada_passo_com_frase_guia() {
        OrgId org = novaOrg();
        pendencia(org, "Entrada A", "ENTRADA", 0);
        pendencia(org, "Entrada B", "ENTRADA", 0);

        RevisaoDados.Conducao c = QuarkusTransaction.requiringNew().call(() -> revisao.calcular(org)).conducao();

        assertTrue(c.entrada().contains("2"), "narra a contagem da entrada: " + c.entrada());
        assertTrue(c.adiados().toLowerCase().contains("nada"), "sem adiados: " + c.adiados());
        assertTrue(c.resumo() != null && !c.resumo().isBlank(), "resumo guiado");
    }

    @Test
    void isolamento_por_organizacao() {
        OrgId a = novaOrg();
        OrgId b = novaOrg();
        pendencia(a, "Da A", "ENTRADA", 0);
        pendencia(a, "Da A 2", "ENTRADA", 0);
        pendencia(b, "Da B", "ENTRADA", 0);

        RadarDados ra = QuarkusTransaction.requiringNew().call(() -> radar.calcular(a));
        RadarDados rb = QuarkusTransaction.requiringNew().call(() -> radar.calcular(b));
        assertEquals(2, ra.dependeDoGestor().total());
        assertEquals(1, rb.dependeDoGestor().total(), "o radar de B nunca conta itens de A");
    }

    // ---- helpers de seed -----------------------------------------------------

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    private UUID pendencia(OrgId org, String titulo, String status, int adiado) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO pendencia (id, org_id, titulo, status, adiado_count, horizonte, classe)
                VALUES (?, ?, ?, ?, ?, 'SEMANA', 'DECISAO')
                """).setParameter(1, id).setParameter(2, org.valor()).setParameter(3, titulo)
                .setParameter(4, status).setParameter(5, adiado).executeUpdate());
        return id;
    }

    private void delegacao(OrgId org, UUID pendencia, String nivel, String status) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO delegacao (id, org_id, pendencia_id, dono_id, nivel, prazo, janela, escalonamento, status)
                VALUES (?, ?, ?, ?, ?, now(), interval '2 minutes', interval '24 hours', ?)
                """).setParameter(1, UUID.randomUUID()).setParameter(2, org.valor())
                .setParameter(3, pendencia).setParameter(4, UUID.randomUUID())
                .setParameter(5, nivel).setParameter(6, status).executeUpdate());
    }

    private void registrar(OrgId org, UUID pendencia, TipoEvento tipo) {
        QuarkusTransaction.requiringNew().run(() -> trilha.registrar(new EventoTrilha(
                org, pendencia, tipo, Ator.sistemaMotor("teste"), null, null, null, null)));
    }
}
