package app.alcada.notificacao.internal;

import app.alcada.notificacao.port.Email;
import app.alcada.notificacao.port.EnviarEmail;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.arc.properties.UnlessBuildProperty;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * E-mail real via SMTP (Quarkus Mailer). Só em {@code prod} — configuração
 * (quarkus.mailer.*) vem do ambiente. Falha vira {@link EmailIndisponivel} e o
 * outbox reprocessa. Idempotência efetiva vem do outbox (marca ENVIADO no sucesso).
 */
@ApplicationScoped
@IfBuildProfile("prod")
@UnlessBuildProperty(name = "linktor.email.real", stringValue = "true")
public class EmailSmtp implements Email {

    private final Mailer mailer;

    public EmailSmtp(Mailer mailer) {
        this.mailer = mailer;
    }

    @Override
    public boolean enviar(OrgId org, EnviarEmail m) {
        try {
            mailer.send(Mail.withText(m.to(), m.assunto(), m.texto()));
            return true;
        } catch (Exception e) {
            throw new EmailIndisponivel("SMTP indisponível: " + e.getMessage());
        }
    }
}
