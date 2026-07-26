package app.alcada.movel.port;

import java.util.UUID;

/**
 * Comando do canal móvel (021): uma intenção já estruturada, com os campos por
 * intenção. A voz (022) produz isto; aqui chega pronto. {@code comandoId} é a
 * chave de idempotência gerada no device.
 */
public record Comando(
        UUID comandoId,
        Intencao intencao,
        UUID pendenciaId,
        Campos campos) {

    public enum Intencao {
        RESOLVER, REPASSAR, RESERVAR, REPOUSAR, ADIAR, REGISTRAR, CONSULTAR
    }

    /**
     * Campos por intenção (os não usados ficam nulos): REPASSAR {dono, nivel, prazo};
     * RESERVAR {prazo}; REPOUSAR/ADIAR {voltaEm, oQueFalta}; RESOLVER {nota};
     * REGISTRAR {titulo, quemEspera, oQueTrava, classe}; CONSULTAR {pergunta}.
     */
    public record Campos(
            UUID dono, String nivel, String prazo, String voltaEm, String oQueFalta,
            String nota, String titulo, String quemEspera, String oQueTrava, String classe,
            String pergunta) {
    }
}
