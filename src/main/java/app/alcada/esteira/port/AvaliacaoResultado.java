package app.alcada.esteira.port;

/** Resultado de avaliar uma instância: desfecho e, se gerou, a pendência anexada. */
public record AvaliacaoResultado(String desfecho, String pendenciaId) {
}
