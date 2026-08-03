package app.alcada.autonomia.port;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Diretório de contatos externos de repasse (RFC-0008). O gestor registra um
 * contato (nome + canal + endereço) para delegar a quem não é usuário do Alçada.
 */
public interface ContatosExternos {

    /** Registra um contato externo e devolve seu id. Canal: WHATSAPP | EMAIL. */
    UUID registrar(OrgId org, String nome, String canal, String endereco, UUID gestorId);
}
