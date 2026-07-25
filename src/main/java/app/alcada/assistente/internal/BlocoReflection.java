package app.alcada.assistente.internal;

import app.alcada.assistente.port.BlocoDados;
import app.alcada.assistente.port.RascunhoResultado;
import io.quarkus.runtime.annotations.RegisterForReflection;

/** DTOs devolvidos/recebidos via {@code Response} — declarados para o native image. */
@RegisterForReflection(targets = {
        BlocoDados.class,
        BlocoDados.ItemDossie.class,
        BlocoDados.Opcao.class,
        RascunhoResultado.class,
        BlocoResource.RedigirReq.class,
        BlocoResource.DecidirReq.class,
        BlocoResource.Problema.class
})
public final class BlocoReflection {
    private BlocoReflection() {
    }
}
