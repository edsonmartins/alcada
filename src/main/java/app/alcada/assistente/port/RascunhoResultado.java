package app.alcada.assistente.port;

/** Rascunho da redação (proposta, INV-10). `disponivel=false` quando não há modelo. */
public record RascunhoResultado(String rascunho, boolean disponivel, String aviso) {
}
