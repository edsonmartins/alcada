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

    /**
     * Resolve deixando um lembrete datado (RFC-0009): o item fecha e o compromisso
     * vira uma pendência nova, dormindo até a data. Lembrete nulo = resolver comum.
     */
    void resolver(OrgId org, UUID pendenciaId, String nota, Lembrete lembrete, UUID gestorId);

    /**
     * Compromisso que sobra de uma decisão ("marquei a reunião pra quinta"). O
     * evento no calendário do gestor entra na F2.3 — aqui é só a data e o texto.
     */
    record Lembrete(OffsetDateTime quando, String texto) {}

    void reservar(OrgId org, UUID pendenciaId, OffsetDateTime agendadoPara, UUID gestorId);

    void repousar(OrgId org, UUID pendenciaId, OffsetDateTime voltaEm, UUID gestorId);

    void descartar(OrgId org, UUID pendenciaId, UUID gestorId);

    String adiar(OrgId org, UUID pendenciaId, OffsetDateTime voltaEm, String oQueFalta, UUID gestorId);
}
