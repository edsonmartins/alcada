package app.alcada.esteira.port;

import java.util.List;

/** Checklist versionado de uma etapa do gestor. */
public record ChecklistDados(String etapaId, int versao, List<CriterioDados> criterios) {
    public record CriterioDados(String chave, String descricao, String tipo, boolean obrigatorio) {
    }
}
