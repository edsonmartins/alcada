package app.alcada.identidade.port;

import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Diretório de pessoas da organização — resolve um nome (possivelmente parcial,
 * como falado) para as pessoas correspondentes. Sempre escopado por org (INV-15).
 * É a ponte que o canal de voz (022) usa para transformar "repassa pro Alexandre"
 * em um {@code pessoa_id} concreto; a decisão de qual pessoa, quando há mais de
 * uma, continua sendo do gestor (INV-10 — o código só oferece candidatos).
 */
public interface Pessoas {

    /**
     * Pessoas cujo nome casa com o termo falado (prefixo de palavra, sem acento).
     * Lista vazia quando ninguém casa. Ordenada por nome.
     */
    List<PessoaRef> buscarPorNome(OrgId org, String termo);

    record PessoaRef(UUID id, String nome) {
    }
}
