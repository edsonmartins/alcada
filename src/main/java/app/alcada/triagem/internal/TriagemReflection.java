package app.alcada.triagem.internal;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** Reflexão para os DTOs da triagem (endpoints devolvem {@code Response}). */
@RegisterForReflection(targets = {
        TriagemResource.ResolverRequest.class,
        TriagemResource.ReservarRequest.class,
        TriagemResource.RepousarRequest.class,
        TriagemResource.AdiarRequest.class,
        TriagemResource.AdiarResposta.class,
        TriagemResource.Problema.class,
        TriagemService.ItemHoje.class
})
public final class TriagemReflection {
    private TriagemReflection() {
    }
}
