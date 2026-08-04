package app.alcada.notificacao.internal;

import java.time.OffsetDateTime;

import app.alcada.notificacao.port.ContasCalendario.Conta;
import app.alcada.notificacao.port.OauthCalendario;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Consentimento simulado: aceita qualquer código não vazio e devolve tokens de
 * mentira. Fora de {@code prod}, sempre — dev/test conectam "calendário" sem
 * falar com o Google. O código {@code recusar} permite exercitar a recusa.
 */
@ApplicationScoped
@UnlessBuildProfile("prod")
public class OauthCalendarioStub implements OauthCalendario {

    @Override
    public Conta trocar(String codigo, String redirectUri) {
        if (codigo == null || codigo.isBlank() || "recusar".equals(codigo)) {
            throw new ConsentimentoInvalido("código de consentimento inválido");
        }
        return new Conta("GOOGLE", "acesso-" + codigo, "refresh-" + codigo,
                OffsetDateTime.now().plusHours(1), "https://www.googleapis.com/auth/calendar.events");
    }
}
