package app.alcada.esteira.port;

import java.util.List;

/**
 * Mineração de checklist (§B): critérios OBJETIVOS recorrentes nas reprovações e,
 * à parte, os apontamentos de JULGAMENTO (que não viram checklist).
 */
public record PropostaChecklist(List<CandidatoCriterio> objetivos, List<String> julgamento) {
    public record CandidatoCriterio(String chave, String descricao, double fracao) {
    }
}
