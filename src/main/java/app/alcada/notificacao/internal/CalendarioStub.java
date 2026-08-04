package app.alcada.notificacao.internal;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import app.alcada.notificacao.port.Calendario;
import app.alcada.notificacao.port.CriarEvento;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Stub de calendário: registra o compromisso; não fala com Google/Microsoft.
 * Fora de {@code prod}, sempre — dev/test nunca mexem na agenda de ninguém por
 * acidente. O adaptador real (OAuth por gestor) entra na fatia F2.3b.
 */
@ApplicationScoped
@UnlessBuildProfile("prod")
public class CalendarioStub implements Calendario {

    private static final Logger LOG = Logger.getLogger(CalendarioStub.class);

    private final Map<String, String> criados = new ConcurrentHashMap<>();
    private final Set<UUID> gestoresSemConta = ConcurrentHashMap.newKeySet();
    private final Set<UUID> gestoresQueFalham = ConcurrentHashMap.newKeySet();
    private final List<CriarEvento> eventos = new CopyOnWriteArrayList<>();
    private final Set<String> cancelados = ConcurrentHashMap.newKeySet();

    @Override
    public String criarEvento(OrgId org, CriarEvento e) {
        if (gestoresSemConta.contains(e.gestorId())) {
            throw new SemConta("gestor sem calendário conectado: " + e.gestorId());
        }
        if (gestoresQueFalham.contains(e.gestorId())) {
            throw new CalendarioIndisponivel("provedor indisponível para " + e.gestorId());
        }
        String jaCriado = criados.get(e.idempotencyKey());
        if (jaCriado != null) {
            return null; // reprocesso: o evento já existe
        }
        String eventoId = "evt-" + UUID.randomUUID();
        criados.put(e.idempotencyKey(), eventoId);
        eventos.add(e);
        LOG.debugf("Calendario(stub) → %s em %s: %s", e.gestorId(), e.quando(), e.titulo());
        return eventoId;
    }

    @Override
    public void cancelarEvento(OrgId org, UUID gestorId, String eventoId) {
        if (gestoresSemConta.contains(gestorId)) {
            throw new SemConta("gestor sem calendário conectado: " + gestorId);
        }
        if (gestoresQueFalham.contains(gestorId)) {
            throw new CalendarioIndisponivel("provedor indisponível para " + gestorId);
        }
        cancelados.add(eventoId);
        criados.values().removeIf(eventoId::equals);
        eventos.removeIf(e -> eventoId.equals(criados.get(e.idempotencyKey())));
    }

    public List<CriarEvento> eventos() {
        return eventos;
    }

    public Set<String> cancelados() {
        return cancelados;
    }

    public void programarSemConta(UUID gestorId) {
        gestoresSemConta.add(gestorId);
    }

    public void programarFalha(UUID gestorId) {
        gestoresQueFalham.add(gestorId);
    }

    public void limpar() {
        criados.clear();
        gestoresSemConta.clear();
        gestoresQueFalham.clear();
        eventos.clear();
        cancelados.clear();
    }
}
