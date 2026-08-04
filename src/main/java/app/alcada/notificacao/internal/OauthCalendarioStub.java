package app.alcada.notificacao.internal;

import java.time.OffsetDateTime;

import app.alcada.notificacao.port.ContasCalendario.Conta;
import app.alcada.notificacao.port.OauthCalendario;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Consentimento simulado: aceita qualquer código não vazio e devolve tokens de
 * mentira. Vale sempre que {@code alcada.calendario.real} não estiver ligado —
 * dev/test conectam "calendário" sem falar com o Google. O código {@code recusar}
 * permite exercitar a recusa.
 */
@ApplicationScoped
@UnlessBuildProperty(name = "alcada.calendario.real", stringValue = "true")
public class OauthCalendarioStub implements OauthCalendario {

    /**
     * Volta direto para o callback com um código de mentira: em dev dá para
     * percorrer a tela inteira sem passar pelo Google.
     */
    @Override
    public String urlConsentimento(String redirectUri, String state) {
        String sep = redirectUri.contains("?") ? "&" : "?";
        return redirectUri + sep + "code=dev-" + state + "&state=" + state;
    }

    @Override
    public Conta trocar(String codigo, String redirectUri) {
        if (codigo == null || codigo.isBlank() || "recusar".equals(codigo)) {
            throw new ConsentimentoInvalido("código de consentimento inválido");
        }
        return new Conta("GOOGLE", "acesso-" + codigo, "refresh-" + codigo,
                OffsetDateTime.now().plusHours(1), "https://www.googleapis.com/auth/calendar.events");
    }
}
