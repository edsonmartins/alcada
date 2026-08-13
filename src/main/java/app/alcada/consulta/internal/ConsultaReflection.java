package app.alcada.consulta.internal;

import app.alcada.consulta.port.ResultadoConsulta;
import io.quarkus.runtime.annotations.RegisterForReflection;

/** DTOs devolvidos/recebidos via {@code Response} — declarados para o native image. */
@RegisterForReflection(targets = {
        ResultadoConsulta.class,
        ResultadoConsulta.Item.class,
        ResultadoConsulta.Link.class,
        ConsultaResource.Req.class,
        ConsultaResource.Problema.class
})
public final class ConsultaReflection {
    private ConsultaReflection() {
    }
}
