package app.alcada.autonomia.port;

import java.time.OffsetDateTime;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Delegação publicada para outros módulos (ex.: canal móvel, 021). Mesmo
 * comportamento do endpoint web, incluindo a janela de reversibilidade (INV-14):
 * nenhum efeito externo antes de fechada a janela.
 */
public interface Autonomia {

    /** Delega a pendência ao dono interno no nível pedido, com prazo. Devolve o id da delegação. */
    UUID delegar(OrgId org, UUID pendenciaId, UUID donoId, String nivelPedido,
                 OffsetDateTime prazo, UUID gestorId);

    /**
     * Delega a um destino interno (pessoa) ou externo (contato) — RFC-0008.
     * No externo, enfileira o aviso de repasse (WhatsApp/e-mail) via outbox.
     */
    UUID delegar(OrgId org, UUID pendenciaId, DestinoRepasse destino, String nivelPedido,
                 OffsetDateTime prazo, UUID gestorId);

    /** Repasse com uma explicação escrita pelo gestor para o destinatário externo. */
    default UUID delegar(OrgId org, UUID pendenciaId, DestinoRepasse destino, String nivelPedido,
                         OffsetDateTime prazo, UUID gestorId, String mensagemRepasse) {
        return delegar(org, pendenciaId, destino, nivelPedido, prazo, gestorId);
    }
}
