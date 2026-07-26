package app.alcada.plataforma.trilha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.alcada.plataforma.trilha.internal.RolagemParticoes;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/**
 * Rolagem de partição mensal da trilha: cria a partição futura antes do uso e é
 * idempotente. A partição DEFAULT deve permanecer vazia.
 */
@QuarkusTest
class RolagemParticoesTest {

    @Inject RolagemParticoes rolagem;
    @Inject EntityManager em;

    @Test
    void garante_particao_futura_antes_do_uso_e_e_idempotente() {
        // 8 meses à frente ultrapassa a janela criada na migration inicial (6 meses)
        rolagem.garantirJanela(8);
        rolagem.garantirJanela(8); // idempotente: não recria nem falha

        boolean existe = QuarkusTransaction.requiringNew().call(() ->
                (Boolean) em.createNativeQuery(
                        "SELECT to_regclass('public.trilha_' "
                        + "|| to_char((date_trunc('month', now()) + interval '8 month'), 'YYYY_MM')) "
                        + "IS NOT NULL").getSingleResult());
        assertTrue(existe, "partição do mês +8 deveria existir após a rolagem");
    }

    @Test
    void particao_default_permanece_vazia() {
        assertEquals(0L, rolagem.linhasNaDefault(),
                "nenhuma escrita deve cair na DEFAULT (partições mensais cobrem o presente)");
    }

    // 004 — arquivamento frio: partição além da retenção é destacada, não deletada.
    @Test
    void arquiva_frias_destaca_particao_antiga_sem_deletar() {
        // cria uma partição de 30 meses atrás
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                "SELECT trilha_cria_particao((date_trunc('month', now()) - interval '30 month')::date)")
                .getSingleResult());
        String nome = QuarkusTransaction.requiringNew().call(() -> (String) em.createNativeQuery(
                "SELECT 'trilha_' || to_char((date_trunc('month', now()) - interval '30 month'), 'YYYY_MM')")
                .getSingleResult());

        int destacadas = rolagem.arquivarFrias(24);
        assertTrue(destacadas >= 1, "a partição de 30 meses atrás deve ser destacada");

        // não é mais partição de trilha (saiu do caminho quente)...
        boolean aindaAnexada = QuarkusTransaction.requiringNew().call(() -> (Boolean) em.createNativeQuery("""
                SELECT EXISTS (SELECT 1 FROM pg_inherits i
                    JOIN pg_class c ON c.oid = i.inhrelid
                    JOIN pg_class p ON p.oid = i.inhparent
                    WHERE p.relname = 'trilha' AND c.relname = ?)
                """).setParameter(1, nome).getSingleResult());
        assertTrue(!aindaAnexada, "partição destacada não é mais filha de trilha");

        // ...mas a tabela continua existindo (imutável: nada deletado).
        boolean tabelaExiste = QuarkusTransaction.requiringNew().call(() -> (Boolean) em.createNativeQuery(
                "SELECT to_regclass('public.' || ?) IS NOT NULL").setParameter(1, nome).getSingleResult());
        assertTrue(tabelaExiste, "a tabela destacada persiste (arquivo frio)");
    }
}
