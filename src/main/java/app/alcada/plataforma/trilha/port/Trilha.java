package app.alcada.plataforma.trilha.port;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Porta de escrita da trilha. Só registra — nunca altera nem apaga (INV-11).
 * O registro participa da transação corrente do chamador.
 */
public interface Trilha {

    /**
     * Registra um evento e devolve seu id. O id permite que uma correção
     * posterior o referencie via {@link #compensar}.
     */
    UUID registrar(EventoTrilha evento);

    /**
     * Único mecanismo de correção (INV-11): grava um evento {@code COMPENSACAO}
     * referenciando {@code eventoCompensadoId}. O evento original permanece
     * intacto — não há caminho de UPDATE. O contexto (org, pendência, ator) é
     * fornecido pelo chamador; não se consulta a trilha para obtê-lo.
     *
     * @return o id do evento de compensação criado
     */
    UUID compensar(OrgId org, UUID pendenciaId, Ator ator, UUID eventoCompensadoId, String motivo);
}
