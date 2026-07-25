package app.alcada.esteira.port;

import java.time.OffsetDateTime;

/** Passagem de uma entidade externa pela esteira. */
public record InstanciaDados(String id, String entidadeExterna, String etapaAtualId,
                             String etapaAtualNome, String status, OffsetDateTime entrouEm) {
}
