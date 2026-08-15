package app.alcada.notificacao.internal;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import app.alcada.notificacao.port.Email;
import app.alcada.notificacao.port.EnviarEmail;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.arc.profile.UnlessBuildProfile;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Stub de e-mail: registra a intenção de envio; não fala com SMTP. Fora de
 * {@code prod}, sempre — garante que dev/test NUNCA enviam e-mail por acidente
 * (o bean real {@link EmailSmtp} só existe em {@code prod}).
 */
@ApplicationScoped
@UnlessBuildProfile("prod")
@UnlessBuildProperty(name = "linktor.email.real", stringValue = "true")
public class EmailStub implements Email {

    private static final Logger LOG = Logger.getLogger(EmailStub.class);

    private final Set<String> entregues = ConcurrentHashMap.newKeySet();
    private final Set<String> destinosQueFalham = ConcurrentHashMap.newKeySet();
    private final List<EnviarEmail> enviados = new CopyOnWriteArrayList<>();

    @Override
    public boolean enviar(OrgId org, EnviarEmail m) {
        if (destinosQueFalham.contains(m.to())) {
            throw new EmailIndisponivel("SMTP indisponível para " + m.to());
        }
        if (!entregues.add(m.idempotencyKey())) {
            return false; // já enviado — idempotente
        }
        enviados.add(m);
        LOG.debugf("Email(stub) → %s: %s", m.to(), m.assunto());
        return true;
    }

    public List<EnviarEmail> enviados() {
        return enviados;
    }

    public void programarFalha(String to) {
        destinosQueFalham.add(to);
    }

    public void limpar() {
        entregues.clear();
        destinosQueFalham.clear();
        enviados.clear();
    }
}
