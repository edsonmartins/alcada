package app.alcada.notificacao.internal;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import app.alcada.captura.port.EnviarAvisoGrupo;
import app.alcada.captura.port.EnviarDireto;
import app.alcada.captura.port.EnviarMensagem;
import app.alcada.notificacao.port.Canal;
import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Stub do Linktor (ADR-0021): registra a intenção de envio; não fala com
 * WhatsApp/e-mail. Contrato explícito de sucesso e falha (idempotente + falha
 * programável).
 *
 * <p>É o default seguro: dev/test nunca batem no host externo por acidente.
 * O bean real ({@link LinktorHttp}) só entra quando o build ativa explicitamente
 * {@code linktor.real=true}.
 */
@ApplicationScoped
@UnlessBuildProperty(name = "linktor.real", stringValue = "true")
public class LinktorStub implements Canal {

    private static final Logger LOG = Logger.getLogger(LinktorStub.class);

    private final Set<String> entregues = ConcurrentHashMap.newKeySet();
    private final Set<String> destinosQueFalham = ConcurrentHashMap.newKeySet();
    private final List<EnviarMensagem> enviadas = new CopyOnWriteArrayList<>();
    private final List<EnviarAvisoGrupo> avisos = new CopyOnWriteArrayList<>();
    private final List<EnviarDireto> diretas = new CopyOnWriteArrayList<>();

    @Override
    public boolean enviar(OrgId org, EnviarMensagem m) {
        if (destinosQueFalham.contains(m.destino())) {
            throw new CanalIndisponivel("canal indisponível para " + m.destino());
        }
        if (!entregues.add(m.idempotencyKey())) {
            return false; // já entregue — idempotente
        }
        enviadas.add(m);
        LOG.debugf("Linktor(stub) → %s [%s]: %s", m.destino(), m.canal(), m.texto());
        return true;
    }

    @Override
    public boolean enviarAvisoGrupo(OrgId org, EnviarAvisoGrupo a) {
        if (destinosQueFalham.contains(a.grupoId())) {
            throw new CanalIndisponivel("canal indisponível para grupo " + a.grupoId());
        }
        if (!entregues.add(a.idempotencyKey())) {
            return false; // já publicado — idempotente
        }
        avisos.add(a);
        LOG.debugf("Linktor(stub) aviso → grupo %s [%s]: %s", a.grupoId(), a.channelId(), a.texto());
        return true;
    }

    @Override
    public boolean enviarDireto(OrgId org, EnviarDireto m) {
        if (destinosQueFalham.contains(m.to())) {
            throw new CanalIndisponivel("canal indisponível para " + m.to());
        }
        if (!entregues.add(m.idempotencyKey())) {
            return false; // já enviado — idempotente
        }
        diretas.add(m);
        LOG.debugf("Linktor(stub) direto → %s [canal %s]: %s", m.to(), m.channelId(), m.texto());
        return true;
    }

    public List<EnviarMensagem> enviadas() {
        return enviadas;
    }

    public List<EnviarDireto> diretas() {
        return diretas;
    }

    public List<EnviarAvisoGrupo> avisos() {
        return avisos;
    }

    // ---- controle para testes / operação ----------------------------------

    public void programarFalha(String destino) {
        destinosQueFalham.add(destino);
    }

    public void repararCanal(String destino) {
        destinosQueFalham.remove(destino);
    }

    public boolean entregou(String idempotencyKey) {
        return entregues.contains(idempotencyKey);
    }

    public int totalEntregue() {
        return entregues.size();
    }

    public void limpar() {
        entregues.clear();
        destinosQueFalham.clear();
        enviadas.clear();
        diretas.clear();
    }
}
