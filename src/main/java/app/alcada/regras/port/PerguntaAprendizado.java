package app.alcada.regras.port;

import java.util.List;

/**
 * Pergunta de aprendizado situada (RFC-0003, ADR-0019): sugere transformar o
 * padrão de uma classe em regra, sempre com evidência clicável (os casos).
 */
public record PerguntaAprendizado(
        String id,
        String classe,
        String nivelSugerido,
        String donoSugerido,
        long ocorrencias,
        List<PropostaRegra.Caso> casos) {
}
