package app.alcada.autonomia.internal;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Reflexão para os DTOs dos endpoints do motor (os métodos devolvem
 * {@code Response}, então o Quarkus não os infere pela assinatura).
 */
@RegisterForReflection(targets = {
        AutonomiaResource.DelegarRequest.class,
        AutonomiaResource.DelegacaoCriada.class,
        AutonomiaResource.Problema.class,
        DelegacaoResource.ProporRequest.class,
        DelegacaoResource.ConcluirRequest.class,
        DelegacaoResource.DevolverRequest.class,
        DelegacaoResource.DelegacaoResumo.class,
        ContatosResource.CriarContato.class,
        ContatosResource.ContatoCriado.class,
        ContatosResource.ContatoView.class,
        ContatosResource.Problema.class
})
public final class AutonomiaReflection {
    private AutonomiaReflection() {
    }
}
