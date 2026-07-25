package app.alcada.plataforma.gateway.port;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Tarefas e resultados do {@link ModelGateway}. Toda tarefa declara
 * {@link Sensibilidade}; o gateway decide o destino. O texto de uma tarefa
 * {@code INTERNA} já chega minimizado — o gateway nunca minimiza (isso é de
 * {@code captura}, RFC-0007).
 */
public final class Tarefas {

    private Tarefas() {
    }

    /**
     * Extração com schema estrito. O {@code mapeador} converte o JSON validado no
     * tipo de domínio de forma explícita (sem reflexão — native-safe).
     */
    public record TarefaExtracao<T>(
            OrgId org,
            Sensibilidade sensibilidade,
            UUID refMensagemId,
            String texto,
            String schemaJson,
            Function<String, T> mapeador) {
    }

    /** Resultado da extração. {@code confianca} nula = extração pendente (reprocesso). */
    public record Extracao<T>(T valor, Double confianca) {
        public static <T> Extracao<T> pendente() {
            return new Extracao<>(null, null);
        }
    }

    public record TarefaRedacao(
            OrgId org, Sensibilidade sensibilidade, UUID refMensagemId, String contexto, String tom) {
    }

    public record Redacao(String rascunho) {
    }

    public record TarefaClassificacao(
            OrgId org, Sensibilidade sensibilidade, UUID refMensagemId, String texto, List<String> classes) {
    }

    public record Classificacao(String classe, double confianca) {
    }

    public record TarefaEmbedding(
            OrgId org, Sensibilidade sensibilidade, UUID refMensagemId, String texto) {
    }

    public record Embedding(float[] vetor) {
    }
}
