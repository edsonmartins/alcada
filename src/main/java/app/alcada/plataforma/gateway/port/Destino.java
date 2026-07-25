package app.alcada.plataforma.gateway.port;

/**
 * Destino efetivo de uma tarefa, decidido pelo gateway a partir da
 * {@link Sensibilidade} e do SKU do tenant.
 */
public enum Destino {
    /** Gateway externo (OpenRouter, com política fixa). */
    EXTERNO,
    /** Inferência local (SKU Soberano ou classe RESTRITA). */
    LOCAL
}
