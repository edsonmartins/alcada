package app.alcada.autonomia.port;

import java.util.UUID;

/**
 * Destino de um repasse (RFC-0008): membro interno (tem fila no Alçada) ou
 * contato externo (WhatsApp/e-mail, não é usuário). O código decide o canal a
 * partir do tipo; o assistente apenas propõe (INV-10).
 */
public sealed interface DestinoRepasse permits DestinoRepasse.Interno, DestinoRepasse.Externo {

    /** Membro do tenant, identificado por pessoa. Recebe pela própria fila (pull). */
    record Interno(UUID pessoaId) implements DestinoRepasse {}

    /** Contato externo, identificado por contato_externo. Recebe pelo canal (aviso). */
    record Externo(UUID contatoId) implements DestinoRepasse {}
}
