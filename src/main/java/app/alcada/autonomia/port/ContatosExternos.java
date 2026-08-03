package app.alcada.autonomia.port;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Diretório de contatos externos de repasse (RFC-0008). O gestor registra um
 * contato (nome + canal + endereço) para delegar a quem não é usuário do Alçada.
 */
public interface ContatosExternos {

    /** Canais de aviso aceitos — o chamador valida antes de registrar. */
    Set<String> CANAIS = Set.of("WHATSAPP", "EMAIL");

    /** Registra um contato externo e devolve seu id. Canal: WHATSAPP | EMAIL. */
    UUID registrar(OrgId org, String nome, String canal, String endereco, UUID gestorId);

    /** Lista os contatos externos do tenant. */
    List<ContatoExterno> listar(OrgId org);

    /** Busca um contato do tenant. Vazio se não existe ou é de outra organização (INV-15). */
    Optional<ContatoExterno> buscar(OrgId org, UUID contatoId);

    /**
     * Contatos que casam com o termo falado — mesma regra do diretório de pessoas
     * (prefixo de palavra, sem acento). Vazio quando ninguém casa: aí o chamador
     * pergunta ao gestor (INV-10), nunca escolhe.
     */
    List<ContatoExterno> buscarPorNome(OrgId org, String termo);

    /** Contato externo (leitura). O endereço é operacional, não identidade. */
    record ContatoExterno(UUID id, String nome, String canal, String endereco) {}
}
