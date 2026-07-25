package app.alcada.captura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import app.alcada.captura.port.Minimizacao;
import app.alcada.captura.port.Minimizador;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Fronteira do minimizador (ADR-0020 §3): nenhum identificador direto atravessa
 * e a re-hidratação não vaza token entre itens.
 */
@QuarkusTest
class MinimizadorTest {

    @Inject Minimizador minimizador;

    @Test
    void nenhum_identificador_direto_atravessa_a_fronteira() {
        String texto = "Fulano de Tal, da Acme Ltda, pediu reembolso. "
                + "CPF 123.456.789-09, e-mail fulano@example.com, fone (11) 91234-5678.";

        Minimizacao m = minimizador.minimizar(texto, List.of("Fulano de Tal"), List.of("Acme Ltda"));
        String out = m.textoMinimizado();

        assertFalse(out.contains("Fulano de Tal"), "nome de pessoa não sai");
        assertFalse(out.contains("Acme Ltda"), "razão social não sai");
        assertFalse(out.matches("(?s).*\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}.*"), "CPF não sai");
        assertFalse(out.contains("fulano@example.com"), "e-mail não sai");
        assertFalse(out.contains("91234-5678"), "telefone não sai");

        assertTrue(out.contains("PESSOA_1"), "pessoa pseudonimizada");
        assertTrue(out.contains("EMPRESA_1"), "empresa pseudonimizada");
    }

    @Test
    void rehidratacao_nao_vaza_token_entre_itens() {
        Minimizacao a = minimizador.minimizar("Ana aprovou o pedido", List.of("Ana"), List.of());
        Minimizacao b = minimizador.minimizar("Bruno recusou o pedido", List.of("Bruno"), List.of());

        // Cada item tem seu próprio PESSOA_1: o mesmo token re-hidrata para nomes
        // diferentes conforme o mapa do item — nunca cruza.
        assertEquals("Ana confirmou", a.rehidratar("PESSOA_1 confirmou"));
        assertEquals("Bruno confirmou", b.rehidratar("PESSOA_1 confirmou"));

        // O mapa de A jamais produz o nome de B.
        assertFalse(a.rehidratar("PESSOA_1 confirmou").contains("Bruno"));
    }
}
