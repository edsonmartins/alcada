package app.alcada.identidade.port;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Operações sobre titulares (LGPD). A eliminação opera por pseudonimização em
 * {@code identidade}, preservando a cadeia da trilha (ADR-0016): as referências
 * por id continuam válidas e a trilha imutável não é tocada.
 */
public interface Titulares {

    /**
     * Pseudonimiza o titular: substitui o nome por um rótulo e remove o e-mail.
     * O id (a referência usada pela trilha) permanece.
     */
    void pseudonimizar(OrgId org, UUID pessoaId);
}
