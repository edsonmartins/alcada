package app.alcada.consulta.port;

import java.util.List;

/**
 * Resultado de uma consulta em linguagem natural. {@code resposta} é o texto
 * pronto para exibir; {@code itens} são a fonte navegável (cada um linka para o
 * bloco/trilha). {@code template} identifica qual consulta da whitelist rodou —
 * {@code "DESCONHECIDO"} quando nada casou (a resposta então diz que não sabe).
 */
public record ResultadoConsulta(String pergunta, String template, String resposta, List<Item> itens) {

    public record Item(String id, String titulo, String classe, Double valorEmJogo) {
    }
}
