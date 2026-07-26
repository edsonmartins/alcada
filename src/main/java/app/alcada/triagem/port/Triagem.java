package app.alcada.triagem.port;

import java.time.OffsetDateTime;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Saídas de triagem publicadas para outros módulos (ex.: canal móvel, 021). O
 * comportamento é o mesmo dos endpoints web; aqui só se expõe o contrato. Cada
 * operação exige a pendência em ENTRADA (senão {@code EstadoInvalido}).
 */
public interface Triagem {

    /** A pendência existe e está em ENTRADA (i.e., ainda é despachável)? */
    boolean emEntrada(OrgId org, UUID pendenciaId);

    void resolver(OrgId org, UUID pendenciaId, String nota, UUID gestorId);

    void reservar(OrgId org, UUID pendenciaId, OffsetDateTime agendadoPara, UUID gestorId);

    void repousar(OrgId org, UUID pendenciaId, OffsetDateTime voltaEm, UUID gestorId);

    void descartar(OrgId org, UUID pendenciaId, UUID gestorId);

    String adiar(OrgId org, UUID pendenciaId, OffsetDateTime voltaEm, String oQueFalta, UUID gestorId);
}
