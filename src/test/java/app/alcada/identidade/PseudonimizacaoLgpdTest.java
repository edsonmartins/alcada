package app.alcada.identidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import app.alcada.identidade.port.Titulares;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.ConsultaTrilha;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/**
 * Eliminação LGPD (ADR-0016): pseudonimiza o titular em {@code identidade}
 * preservando a cadeia da trilha. A trilha imutável não é tocada; a referência
 * por {@code pessoa_id} permanece válida.
 */
@QuarkusTest
class PseudonimizacaoLgpdTest {

    @Inject Titulares titulares;
    @Inject Trilha trilha;
    @Inject ConsultaTrilha consulta;
    @Inject EntityManager em;

    @Test
    void eliminacao_preserva_a_cadeia() {
        OrgId org = new OrgId(UUID.randomUUID());
        UUID pessoaId = UUID.randomUUID();
        UUID pend = UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, ?)")
                    .setParameter(1, org.valor()).setParameter(2, "Org Piloto").executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO pessoa (id, org_id, nome, email, criada_em) VALUES (?, ?, ?, ?, now())")
                    .setParameter(1, pessoaId).setParameter(2, org.valor())
                    .setParameter(3, "Fulano de Tal").setParameter(4, "fulano@example.com")
                    .executeUpdate();
            trilha.registrar(new EventoTrilha(org, pend, TipoEvento.RESOLVIDA,
                    Ator.humano(pessoaId), "ENTRADA", "FECHADA", null, null));
        });

        QuarkusTransaction.requiringNew().run(() -> titulares.pseudonimizar(org, pessoaId));

        // Titular pseudonimizado em identidade
        Object[] pessoa = QuarkusTransaction.requiringNew().call(() -> (Object[])
                em.createNativeQuery("SELECT nome, email FROM pessoa WHERE org_id = ? AND id = ?")
                        .setParameter(1, org.valor()).setParameter(2, pessoaId)
                        .getSingleResult());
        assertTrue(((String) pessoa[0]).startsWith("PESSOA_"), "nome pseudonimizado");
        assertNull(pessoa[1], "e-mail removido");

        // Cadeia da trilha intacta: o evento continua referenciando o pessoa_id
        var eventos = QuarkusTransaction.requiringNew().call(() -> consulta.daPendencia(org, pend));
        assertEquals(1, eventos.size());
        assertTrue(eventos.get(0).ator().contains(pessoaId.toString()),
                "a referência por id permanece válida na trilha imutável");
    }
}
