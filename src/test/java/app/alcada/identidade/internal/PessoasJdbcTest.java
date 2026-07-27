package app.alcada.identidade.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import app.alcada.identidade.port.Pessoas;
import app.alcada.identidade.port.Pessoas.PessoaRef;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/**
 * Diretório de pessoas + memória de apelidos (022) sobre o banco real: exclusão do
 * gestor, casamento por nome sem acento, prioridade do apelido aprendido e o
 * descarte de apelidos redundantes (que já casariam pelo nome).
 */
@QuarkusTest
class PessoasJdbcTest {

    @Inject Pessoas pessoas;
    @Inject EntityManager em;

    @Test
    void casaPorNomeSemAcentoEExcluiOGestor() {
        OrgId org = novaOrg();
        UUID gestor = pessoa(org, "Gestor Piloto");
        UUID exec = pessoa(org, "Alexandre Silva");

        List<PessoaRef> r = buscar(org, gestor, "alexandre");
        assertEquals(1, r.size());
        assertEquals(exec, r.get(0).id());

        assertTrue(buscar(org, gestor, "gestor").isEmpty(), "o próprio gestor nunca é candidato");
    }

    @Test
    void apelidoAprendidoTemPrioridade() {
        OrgId org = novaOrg();
        UUID gestor = pessoa(org, "Gestor Piloto");
        UUID alex = pessoa(org, "Alexandre Silva");

        assertTrue(buscar(org, gestor, "xandão").isEmpty(), "apelido ainda não existe");

        QuarkusTransaction.requiringNew().run(() -> pessoas.aprender(org, gestor, "Xandão", alex));

        List<PessoaRef> r = buscar(org, gestor, "xandao"); // sem acento, mesmo termo
        assertEquals(1, r.size());
        assertEquals(alex, r.get(0).id());
        assertEquals(1L, apelidos(org, gestor));
    }

    @Test
    void naoAprendeApelidoRedundante() {
        OrgId org = novaOrg();
        UUID gestor = pessoa(org, "Gestor Piloto");
        UUID alex = pessoa(org, "Alexandre Silva");

        // "alexandre" já casa pelo nome → não vira apelido.
        QuarkusTransaction.requiringNew().run(() -> pessoas.aprender(org, gestor, "Alexandre", alex));
        assertEquals(0L, apelidos(org, gestor));
    }

    @Test
    void listarTrazEquipeSemOGestor() {
        OrgId org = novaOrg();
        UUID gestor = pessoa(org, "Gestor Piloto");
        pessoa(org, "Alexandre Silva");
        pessoa(org, "Bruno Costa");

        List<PessoaRef> r = QuarkusTransaction.requiringNew().call(() -> pessoas.listar(org, gestor));
        assertEquals(2, r.size());
        assertTrue(r.stream().noneMatch(p -> p.id().equals(gestor)));
    }

    // ---- helpers -----------------------------------------------------------

    private List<PessoaRef> buscar(OrgId org, UUID gestor, String termo) {
        return QuarkusTransaction.requiringNew().call(() -> pessoas.buscarPorNome(org, gestor, termo));
    }

    private long apelidos(OrgId org, UUID gestor) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "SELECT count(*) FROM apelido_pessoa WHERE org_id = ? AND gestor_id = ?")
                .setParameter(1, org.valor()).setParameter(2, gestor).getSingleResult())).longValue();
    }

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }

    private UUID pessoa(OrgId org, String nome) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                INSERT INTO pessoa (id, org_id, nome, email, criada_em)
                VALUES (?, ?, ?, null, now())
                """).setParameter(1, id).setParameter(2, org.valor()).setParameter(3, nome).executeUpdate());
        return id;
    }
}
