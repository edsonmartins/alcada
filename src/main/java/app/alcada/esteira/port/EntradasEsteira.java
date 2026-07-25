package app.alcada.esteira.port;

/** Value objects de entrada dos comandos da esteira. */
public final class EntradasEsteira {
    private EntradasEsteira() {
    }

    public record NovaEtapa(int ordem, String nome, String donoId, String sla, boolean etapaDoGestor) {
    }

    public record NovoCriterio(String chave, String descricao, String tipo, boolean obrigatorio) {
    }

    public record ResultadoItem(String criterioChave, String resultado) {   // OK | FALHOU | NAO_APLICA
    }

    public record ApontamentoItem(String texto, String tipo) {              // OBJETIVO | JULGAMENTO
    }
}
