package app.alcada.captura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import app.alcada.captura.internal.ExpurgoEventoBruto;
import app.alcada.captura.internal.Ingestao;
import app.alcada.captura.port.MensagemRecebida;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/** Idempotência de reentrega (ADR-0021) e expurgo do bruto (ADR-0011). */
@QuarkusTest
class IngestaoTest {

    @Inject Ingestao ingestao;
    @Inject ExpurgoEventoBruto expurgo;
    @Inject EntityManager em;

    @Test
    void reentrega_da_mesma_mensagem_e_idempotente() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID fonte = criarOrgEFonte(org);
        String mensagemId = "msg-" + UUID.randomUUID();
        MensagemRecebida m = new MensagemRecebida(
                "canal", fonte.toString(), "joao", "t", "@alcada oi", List.of(), mensagemId, false, null);

        UUID primeiro = ingestao.ingerir(org, m);
        UUID segundo = ingestao.ingerir(org, m); // reentrega

        assertNotNull(primeiro);
        assertNull(segundo, "reentrega não cria segundo bruto");
        assertEquals(1L, contar(org, "SELECT count(*) FROM evento_bruto WHERE org_id = ?"));
        assertEquals(1L, contar(org, "SELECT count(*) FROM job WHERE org_id = ? AND tipo = 'PROCESSAR_CAPTURA'"),
                "um único processamento agendado");
    }

    @Test
    void expurgo_remove_bruto_vencido() throws Exception {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID fonte = criarOrgEFonte(org);
        UUID vencido = UUID.randomUUID();
        OffsetDateTime ontem = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO evento_bruto (id, org_id, fonte_id, mensagem_id, texto, expira_em)
                VALUES (?, ?, ?, ?, 'x', ?)
                """)
                .setParameter(1, vencido).setParameter(2, org.valor()).setParameter(3, fonte)
                .setParameter(4, "m-" + vencido).setParameter(5, ontem).executeUpdate());

        expurgo.expurgar();

        assertEquals(0L, contar(org, "SELECT count(*) FROM evento_bruto WHERE org_id = ? AND expira_em < now()"));
    }

    private UUID criarOrgEFonte(OrgId org) {
        UUID fonte = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("INSERT INTO organizacao (id, nome, sku) VALUES (?, 'Org', 'CLOUD')")
                    .setParameter(1, org.valor()).executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO fonte (id, org_id, tipo, identificador, segredo) VALUES (?, ?, 'WHATSAPP', 'g', 's')")
                    .setParameter(1, fonte).setParameter(2, org.valor()).executeUpdate();
        });
        return fonte;
    }

    private long contar(OrgId org, String sql) {
        return ((Number) QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery(sql).setParameter(1, org.valor()).getSingleResult())).longValue();
    }
}
