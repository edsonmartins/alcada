package app.alcada.notificacao.port;

import app.alcada.captura.port.EnviarMensagem;
import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Saída para o canal de origem via Linktor (ADR-0021). Contrato explícito de
 * sucesso E falha: a entrega pode falhar, e o outbox reprocessa.
 */
public interface Canal {

    /**
     * @return {@code true} se entregou agora; {@code false} se já entregue
     *         (idempotente por {@code idempotency_key}).
     * @throws CanalIndisponivel quando a entrega falha (o outbox reprocessa).
     */
    boolean enviar(OrgId org, EnviarMensagem mensagem);

    /** Falha de entrega — o efeito volta para retentativa no outbox. */
    class CanalIndisponivel extends RuntimeException {
        public CanalIndisponivel(String msg) {
            super(msg);
        }
    }
}
