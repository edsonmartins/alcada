package app.alcada.autonomia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import app.alcada.autonomia.port.ContatosExternos;
import app.alcada.autonomia.port.ContatosExternos.ContatoExterno;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

/**
 * Casamento do nome falado contra o diretório de contatos externos (RFC-0008,
 * fatia F1.4c) — mesma regra do diretório de pessoas: prefixo de palavra, sem
 * acento, escopado por organização (INV-15).
 */
@QuarkusTest
class ContatosBuscaTest {

    @Inject ContatosExternos contatos;
    @Inject EntityManager em;

    @Test
    void casa_por_primeiro_nome_e_ignora_acento() {
        OrgId org = novaOrg();
        UUID gestor = UUID.randomUUID();
        contatos.registrar(org, "Clécia Souza", "WHATSAPP", "+5521999990000", gestor);
        contatos.registrar(org, "Marcello Andrade", "EMAIL", "marcello@rioquality.com.br", gestor);

        assertEquals(List.of("Clécia Souza"), nomes(contatos.buscarPorNome(org, "clecia")));
        assertEquals(List.of("Marcello Andrade"), nomes(contatos.buscarPorNome(org, "Marcello")));
        assertEquals(List.of("Clécia Souza"), nomes(contatos.buscarPorNome(org, "Clécia Souza")),
                "todas as palavras precisam casar");
    }

    @Test
    void nome_desconhecido_nao_casa_com_ninguem() {
        OrgId org = novaOrg();
        contatos.registrar(org, "Clécia Souza", "WHATSAPP", "+5521999990000", UUID.randomUUID());

        assertTrue(contatos.buscarPorNome(org, "Fulano").isEmpty());
        assertTrue(contatos.buscarPorNome(org, "  ").isEmpty(), "termo vazio não casa com todos");
        assertTrue(contatos.buscarPorNome(org, null).isEmpty());
    }

    @Test
    void busca_nao_atravessa_organizacoes() {
        OrgId a = novaOrg();
        OrgId b = novaOrg();
        contatos.registrar(b, "Marcello Andrade", "WHATSAPP", "+5521988887777", UUID.randomUUID());

        assertTrue(contatos.buscarPorNome(a, "Marcello").isEmpty(), "A não enxerga o contato de B");
        assertEquals(1, contatos.buscarPorNome(b, "Marcello").size());
    }

    private static List<String> nomes(List<ContatoExterno> achados) {
        return achados.stream().map(ContatoExterno::nome).toList();
    }

    private OrgId novaOrg() {
        OrgId org = new OrgId(UUID.randomUUID());
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("INSERT INTO organizacao (id, nome) VALUES (?, 'Org')")
                        .setParameter(1, org.valor()).executeUpdate());
        return org;
    }
}
