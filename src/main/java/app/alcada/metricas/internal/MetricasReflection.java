package app.alcada.metricas.internal;

import app.alcada.metricas.port.RadarDados;
import app.alcada.metricas.port.RevisaoDados;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Os endpoints devolvem {@code Response}, então o Quarkus não infere os corpos —
 * os DTOs precisam ser declarados para o native image.
 */
@RegisterForReflection(targets = {
        RadarDados.class,
        RadarDados.Dependencia.class,
        RadarDados.ItemAdiado.class,
        RadarDados.PiorEspera.class,
        RadarDados.Autonomia.class,
        RadarDados.FechamentoCanal.class,
        RadarDados.SemanaFluxo.class,
        RevisaoDados.class,
        RevisaoDados.Entrada.class,
        RevisaoDados.ItemFila.class,
        RevisaoDados.DicaRegra.class,
        RevisaoDados.ResumoSemana.class,
        RevisaoDados.Conducao.class,
        RadarResource.Problema.class
})
public final class MetricasReflection {
    private MetricasReflection() {
    }
}
