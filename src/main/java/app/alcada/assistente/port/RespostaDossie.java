package app.alcada.assistente.port;

import java.util.List;

/**
 * Resposta a uma pergunta ao dossiê (RFC-0004 §1). Sempre com fonte; quando nada
 * é recuperado acima do limiar, {@code encontrou=false} e a resposta é a de
 * ausência — nunca completa de memória. {@code correcao} (quando não nula) é a
 * correção de premissa: a base contradiz um fato afirmado na pergunta
 * (ex.: "A pergunta menciona maio, mas a base indica 08/07/2026").
 */
public record RespostaDossie(boolean encontrou, String resposta, List<Fonte> fontes, String correcao) {

    public record Fonte(String fonteTipo, String fonteRef, String trecho) {
    }
}
