package app.alcada.identidade.port;

import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Diretório de pessoas da organização — resolve um nome (possivelmente parcial ou
 * um apelido aprendido, como falado) para as pessoas correspondentes. Sempre
 * escopado por org (INV-15). É a ponte que o canal de voz (022) usa para
 * transformar "repassa pro Alexandre" em um {@code pessoa_id} concreto; a decisão
 * de qual pessoa, quando há mais de uma, continua sendo do gestor (INV-10).
 *
 * <p>A memória de apelidos é durável e por gestor: ao confirmar um repasse, o
 * termo falado passa a apontar direto para a pessoa escolhida ({@link #aprender}).
 * Não é cadastro manual (INV-02) — é aprendido do uso.
 */
public interface Pessoas {

    /**
     * Pessoas que casam com o termo falado, na ordem: primeiro um apelido já
     * aprendido pelo gestor; senão, casamento por nome (prefixo de palavra, sem
     * acento). O próprio gestor nunca é candidato a receber o repasse. Lista vazia
     * quando ninguém casa — aí o chamador oferece {@link #listar} ("quem é?").
     */
    List<PessoaRef> buscarPorNome(OrgId org, UUID gestorId, String termo);

    /** Todas as pessoas da org, exceto o próprio gestor. Para desambiguar por lista. */
    List<PessoaRef> listar(OrgId org, UUID gestorId);

    /**
     * Aprende que {@code termo} (o que o gestor falou) se refere a {@code pessoaId}.
     * Ignora termos que já casariam com o nome da pessoa (redundantes). Idempotente.
     */
    void aprender(OrgId org, UUID gestorId, String termo, UUID pessoaId);

    record PessoaRef(UUID id, String nome) {
    }
}
