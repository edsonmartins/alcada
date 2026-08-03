package app.alcada.movel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import app.alcada.movel.port.Comando;
import app.alcada.movel.port.Comando.Campos;
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

/** Cenários do 021 — sincronização de comandos do canal móvel. */
@QuarkusTest
class ComandoMovelTest {

    @Inject ComandoMovel comandos;
    @Inject EntityManager em;

    // C1 — comando despacha uma pendência
    @Test
    void comando_resolve_uma_pendencia() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        UUID pessoa = UUID.randomUUID();

        var r = sync(org, pessoa, resolver(pend));

        assertEquals(Status.OK, r.get(0).status());
        assertEquals("FECHADA", status(org, pend));
    }

    // C2 — sincronização idempotente (INV-13)
    @Test
    void mesmo_comando_executa_uma_so_vez() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        UUID pessoa = UUID.randomUUID();
        Comando c = resolver(pend);

        sync(org, pessoa, c);
        var r2 = sync(org, pessoa, c); // reenvio do mesmo comandoId

        assertEquals(Status.OK, r2.get(0).status(), "reenvio devolve o resultado gravado");
        assertEquals(1L, countOutbox(org, "item.fechado"), "o efeito ocorre uma só vez");
    }

    // C3 — REPASSAR mantém a janela (nenhum efeito externo imediato)
    @Test
    void comando_repassar_delega_sem_efeito_externo_imediato() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "ENTRADA");
        UUID pessoa = UUID.randomUUID();

        var r = sync(org, pessoa, repassar(pend, UUID.randomUUID(), "N2"));

        assertEquals(Status.OK, r.get(0).status());
        assertEquals("DELEGADA", status(org, pend));
        assertEquals(0L, countOutbox(org, "delegacao.executada_por_ausencia"),
                "nenhum efeito externo antes de fechar a janela (INV-14)");
    }

    // C4 — comando sobre pendência que já saiu da fila
    @Test
    void comando_sobre_pendencia_fechada_e_ignorado() {
        OrgId org = novaOrg();
        UUID pend = pendencia(org, "FECHADA");
        var r = sync(org, UUID.randomUUID(), resolver(pend));
        assertEquals(Status.IGNORADO, r.get(0).status());
    }

    // C5 — CONSULTAR devolve resposta sobre a fila
    @Test
    void comando_consultar_devolve_resposta() {
        OrgId org = novaOrg();
        pendencia(org, "ENTRADA");
        Comando c = new Comando(UUID.randomUUID(), Intencao.CONSULTAR, null,
                campos().pergunta("quanto está esperando por mim").build());

        var r = sync(org, UUID.randomUUID(), c);

        assertEquals(Status.OK, r.get(0).status());
        assertNotNull(r.get(0).consulta(), "traz o resultado da consulta");
        assertEquals("ESPERANDO_MIM", r.get(0).consulta().template());
    }

    // C6 — isolamento por organização (INV-15)
    @Test
    void comando_nao_atinge_outra_organizacao() {
        OrgId a = novaOrg();
        OrgId b = novaOrg();
        UUID pendB = pendencia(b, "ENTRADA");
        // A tenta resolver uma pendência de B
        var r = sync(a, UUID.randomUUID(), resolver(pendB));
        assertEquals(Status.IGNORADO, r.get(0).status(), "A não enxerga a pendência de B");
        assertEquals("ENTRADA", status(b, pendB), "a pendência de B fica intacta");
    }

    // C7 — REGISTRAR (escape) cria item sem depender da fila e nunca é ignorado
    @Test
    void comando_registrar_cria_item_em_entrada() {
        OrgId org = novaOrg();
        Comando c = new Comando(UUID.randomUUID(), Intencao.REGISTRAR, null,
                campos().titulo("cobrar o Panorama").build());
        var r = sync(org, UUID.randomUUID(), c);
        assertEquals(Status.OK, r.get(0).status());
        assertNotNull(r.get(0).pendenciaId());
        assertEquals("ENTRADA", status(org, r.get(0).pendenciaId()));
    }

    // ---- helpers -----------------------------------------------------------

    private List<ResultadoComando> sync(OrgId org, UUID pessoa, Comando c) {
        return comandos.sincronizar(org, pessoa, List.of(c));
    }

    private static Comando resolver(UUID pend) {
        return new Comando(UUID.randomUUID(), Intencao.RESOLVER, pend, null);
    }

    private static Comando repassar(UUID pend, UUID dono, String nivel) {
        return new Comando(UUID.randomUUID(), Intencao.REPASSAR, pend,
                new CamposBuilder().dono(dono).nivel(nivel)
                        .prazo(OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).toString()).build());
    }

    private static CamposBuilder campos() {
        return new CamposBuilder();
    }

    /** Builder enxuto para o record de campos (muitos nulos). */
    static final class CamposBuilder {
        private UUID dono;
        private String nivel, prazo, voltaEm, oQueFalta, nota, titulo, quemEspera, oQueTrava, classe,
                pergunta, aliasFalado;

        CamposBuilder dono(UUID v) { this.dono = v; return this; }
        CamposBuilder nivel(String v) { this.nivel = v; return this; }
        CamposBuilder prazo(String v) { this.prazo = v; return this; }
        CamposBuilder pergunta(String v) { this.pergunta = v; return this; }
        CamposBuilder titulo(String v) { this.titulo = v; return this; }
        CamposBuilder aliasFalado(String v) { this.aliasFalado = v; return this; }

        Campos build() {
            return new Campos(dono, nivel, prazo, voltaEm, oQueFalta, nota, titulo, quemEspera,
                    oQueTrava, classe, pergunta, aliasFalado, null);
        }
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
                VALUES (?, ?, 'Item', 'DECISAO', 'SEMANA', ?, 1000)
                """).setParameter(1, id).setParameter(2, org.valor()).setParameter(3, status).executeUpdate());
        return id;
    }

    private String status(OrgId org, UUID pend) {
        return (String) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT status FROM pendencia WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, pend).getSingleResult());
    }

    private long countOutbox(OrgId org, String tipo) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT count(*) FROM outbox WHERE org_id = ? AND tipo = ?")
                .setParameter(1, org.valor()).setParameter(2, tipo).getSingleResult())).longValue();
    }
}
