package app.alcada.regras.port;

import java.util.List;

/**
 * Candidata a regra de autonomia (RFC-0003 §A): padrão decisório observado, com
 * evidência navegável (ADR-0019). Nunca vira regra sem aceite humano (INV-10).
 */
public record PropostaRegra(
        String classe,
        long ocorrencias,
        double consistencia,
        String nivelSugerido,
        String donoSugerido,
        List<Caso> casos) {

    /** Um caso navegável (abre a trilha da pendência). */
    public record Caso(String pendenciaId, String titulo, String desfecho, Double valorEmJogo) {
    }
}
