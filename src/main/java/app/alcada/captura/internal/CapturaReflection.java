package app.alcada.captura.internal;

import app.alcada.captura.port.MensagemRecebida;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Reflexão para os tipos de entrada/saída dos endpoints de captura. Os métodos
 * devolvem {@code Response}, então o Quarkus não infere os corpos pela
 * assinatura — precisam ser declarados para o native image.
 */
@RegisterForReflection(targets = {
        MensagemRecebida.class,
        CapturaResource.ResultadoIngestao.class,
        CapturaResource.NovaFonte.class,
        CapturaResource.FonteCriada.class,
        CapturaResource.FonteResumo.class,
        CapturaResource.Problema.class,
        PendenciaResource.PendenciaResumo.class,
        PendenciaResource.EscapeRequest.class,
        PendenciaResource.EscapeCriada.class,
        PendenciaResource.DesfundirRequest.class,
        PendenciaResource.DesfundirResposta.class,
        PendenciaResource.Problema.class,
        GruposResource.Grupo.class,
        GruposResource.Ajuste.class,
        GruposResource.Problema.class
})
public final class CapturaReflection {
    private CapturaReflection() {
    }
}
