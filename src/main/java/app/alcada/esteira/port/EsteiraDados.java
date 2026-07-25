package app.alcada.esteira.port;

import java.util.List;

/** Esteira com suas etapas ordenadas (ADR-0012). */
public record EsteiraDados(String id, String nome, List<EtapaDados> etapas) {
    public record EtapaDados(String id, int ordem, String nome, String donoId, boolean etapaDoGestor) {
    }
}
