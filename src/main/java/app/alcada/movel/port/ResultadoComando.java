package app.alcada.movel.port;

import java.util.UUID;

import app.alcada.consulta.port.ResultadoConsulta;

/**
 * Resultado de um comando sincronizado (021). {@code OK} executou; {@code IGNORADO}
 * a pendência já saiu da fila desde a captura offline; {@code RECUSADO} reservado
 * ao modo trajeto (023); {@code ERRO} falha inesperada. {@code consulta} só vem em
 * CONSULTAR.
 */
public record ResultadoComando(
        UUID comandoId,
        Status status,
        String detalhe,
        UUID pendenciaId,
        ResultadoConsulta consulta) {

    public enum Status {
        OK, IGNORADO, RECUSADO, ERRO
    }

    public static ResultadoComando ok(UUID id, UUID pendencia) {
        return new ResultadoComando(id, Status.OK, null, pendencia, null);
    }

    public static ResultadoComando ignorado(UUID id, String detalhe) {
        return new ResultadoComando(id, Status.IGNORADO, detalhe, null, null);
    }

    public static ResultadoComando erro(UUID id, String detalhe) {
        return new ResultadoComando(id, Status.ERRO, detalhe, null, null);
    }
}
