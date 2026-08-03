package app.alcada.autonomia.port;

import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Diretório de contatos externos de repasse (RFC-0008). O gestor registra um
 * contato (nome + canal + endereço) para delegar a quem não é usuário do Alçada.
 */
public interface ContatosExternos {

    /** Registra um contato externo e devolve seu id. Canal: WHATSAPP | EMAIL. */
    UUID registrar(OrgId org, String nome, String canal, String endereco, UUID gestorId);

    /** Lista os contatos externos do tenant. */
    List<ContatoExterno> listar(OrgId org);

    /** Contato externo (leitura). O endereço é operacional, não identidade. */
    record ContatoExterno(UUID id, String nome, String canal, String endereco) {}
}
