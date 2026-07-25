package app.alcada.plataforma.trilha.internal;

import app.alcada.plataforma.trilha.port.EventoRegistrado;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Registro de reflexão para os tipos serializados pelo endpoint da trilha.
 * Necessário porque {@code TrilhaResource} devolve {@code Response} (o Quarkus
 * não infere o tipo do corpo pela assinatura), então esses records precisam ser
 * declarados explicitamente para o native image.
 */
@RegisterForReflection(targets = {EventoRegistrado.class, TrilhaResource.Problema.class})
public final class TrilhaReflection {
    private TrilhaReflection() {
    }
}
