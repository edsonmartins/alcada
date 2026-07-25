package app.alcada.captura.internal;

import java.util.UUID;

import app.alcada.captura.port.CanalSaida;
import app.alcada.captura.port.EnviarMensagem;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.port.MensagemOutbox;
import app.alcada.plataforma.outbox.port.Outbox;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Saída no canal via outbox transacional: a resposta ao canal só existe se a
 * transação de captura confirmar (INV-14). A entrega ao Linktor é feita pelo
 * worker do outbox; até o Linktor existir, o efeito fica enfileirado.
 */
@ApplicationScoped
public class CanalSaidaOutbox implements CanalSaida {

    private final Outbox outbox;

    public CanalSaidaOutbox(Outbox outbox) {
        this.outbox = outbox;
    }

    @Override
    public void responder(OrgId org, UUID pendenciaId, EnviarMensagem m) {
        String payload = "{\"canal\":" + s(m.canal()) + ",\"destino\":" + s(m.destino())
                + ",\"texto\":" + s(m.texto()) + ",\"responder_a\":" + s(m.responderA())
                + ",\"pendencia_id\":\"" + pendenciaId + "\"}";
        outbox.publicar(new MensagemOutbox(org, "canal.resposta", payload, m.idempotencyKey()));
    }

    private static String s(String v) {
        if (v == null) {
            return "null";
        }
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
