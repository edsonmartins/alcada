package app.alcada.regras.internal;

import app.alcada.regras.port.PerguntaAprendizado;
import app.alcada.regras.port.PropostaRegra;
import app.alcada.regras.port.RegraAtiva;
import io.quarkus.runtime.annotations.RegisterForReflection;

/** DTOs devolvidos via {@code Response} — declarados para o native image. */
@RegisterForReflection(targets = {
        PropostaRegra.class,
        PropostaRegra.Caso.class,
        RegraAtiva.class,
        PerguntaAprendizado.class,
        RegrasResource.CriarRegra.class,
        RegrasResource.RegraCriada.class,
        RegrasResource.SilenciarRegra.class,
        RegrasResource.Problema.class,
        AprendizadoResource.RespostaReq.class
})
public final class RegrasReflection {
    private RegrasReflection() {
    }
}
