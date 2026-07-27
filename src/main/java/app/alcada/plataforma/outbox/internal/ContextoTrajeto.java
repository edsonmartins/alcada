package app.alcada.plataforma.outbox.internal;

import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.outbox.port.EscopoTrajeto;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Portador do "trajeto atual" na thread que processa um comando (023). Enquanto
 * ativo, todo efeito externo publicado no outbox nasce represado (marcado com o
 * {@code trajeto_id}) — o worker não o emite até a liberação. É por-thread porque
 * cada comando móvel roda na própria transação/thread; não vaza entre comandos.
 */
@ApplicationScoped
public class ContextoTrajeto implements EscopoTrajeto {

    private static final ThreadLocal<UUID> ATUAL = new ThreadLocal<>();
    private static final ThreadLocal<UUID> PENDENCIA = new ThreadLocal<>();

    @Override
    public void iniciar(UUID trajetoId, UUID pendenciaId) {
        ATUAL.set(trajetoId);
        PENDENCIA.set(pendenciaId);
    }

    @Override
    public void encerrar() {
        ATUAL.remove();
        PENDENCIA.remove();
    }

    @Override
    public Optional<UUID> atual() {
        return Optional.ofNullable(ATUAL.get());
    }

    @Override
    public Optional<UUID> pendencia() {
        return Optional.ofNullable(PENDENCIA.get());
    }
}
