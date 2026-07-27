package app.alcada.plataforma.outbox.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Escopo de "trajeto atual" na thread que processa um comando (023). Entre
 * {@link #iniciar} e {@link #encerrar}, todo efeito externo publicado no outbox
 * nasce represado (marcado com o trajeto) e o worker não o emite até a liberação
 * ({@link Outbox#liberarTrajeto}). É o chamador (canal móvel) quem abre o escopo.
 */
public interface EscopoTrajeto {

    /** Abre o escopo para um comando de trajeto sobre {@code pendenciaId} (pode ser null). */
    void iniciar(UUID trajetoId, UUID pendenciaId);

    /** Sempre em {@code finally} — não pode vazar para o próximo comando. */
    void encerrar();

    /** Trajeto atual, se a thread está processando um comando de trajeto. */
    Optional<UUID> atual();

    /** Pendência do comando de trajeto atual (para desfazer por item). */
    Optional<UUID> pendencia();
}
