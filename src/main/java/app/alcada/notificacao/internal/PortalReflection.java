package app.alcada.notificacao.internal;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** Reflexão para os DTOs do portal (endpoints devolvem {@code Response}). */
@RegisterForReflection(targets = {
        PortalTokens.ProjecaoPublica.class,
        EmissaoPortalResource.EmitirRequest.class,
        EmissaoPortalResource.LinkResposta.class,
        EmissaoPortalResource.Problema.class,
        CalendarioResource.ConectarRequest.class,
        CalendarioResource.EstadoConta.class,
        CalendarioResource.Problema.class
})
public final class PortalReflection {
    private PortalReflection() {
    }
}
