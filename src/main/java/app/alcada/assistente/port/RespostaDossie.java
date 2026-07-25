package app.alcada.assistente.port;

import java.util.List;

/**
 * Resposta a uma pergunta ao dossiê (RFC-0004 §1). Sempre com fonte; quando nada
 * é recuperado acima do limiar, {@code encontrou=false} e a resposta é a de
 * ausência — nunca completa de memória.
 */
public record RespostaDossie(boolean encontrou, String resposta, List<Fonte> fontes) {

    public record Fonte(String fonteTipo, String fonteRef, String trecho) {
    }
}
