package app.alcada.movel.internal;

import app.alcada.movel.port.Comando;
import app.alcada.movel.port.ResultadoComando;
import io.quarkus.runtime.annotations.RegisterForReflection;

/** DTOs recebidos/devolvidos via {@code Response} — declarados para o native image. */
@RegisterForReflection(targets = {
        Comando.class,
        Comando.Campos.class,
        ResultadoComando.class,
        ComandoResource.Lote.class,
        ComandoResource.Resposta.class,
        ComandoResource.Problema.class
})
public final class MovelReflection {
    private MovelReflection() {
    }
}
