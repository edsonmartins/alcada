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
    record Lembrete(OffsetDateTime quando, String texto) {

        /**
         * Um lembrete só serve se for para o futuro e tiver o que dizer. Longe
         * demais quase sempre é data mal interpretada (INV-10: o código valida o
         * que o modelo propôs) — e um lembrete na data errada é pior que nenhum.
         *
         * <p>Mora aqui, e não no serviço, para o chamador poder validar **antes**
         * de abrir a transação do comando: exceção lá dentro marcaria a transação
         * para rollback e derrubaria o registro de idempotência junto.
         */
        public void exigirUtil() {
            if (quando == null || texto == null || texto.isBlank()) {
                throw new IllegalArgumentException("lembrete exige quando e texto");
            }
            OffsetDateTime agora = OffsetDateTime.now(java.time.ZoneOffset.UTC);
            if (!quando.isAfter(agora)) {
                throw new IllegalArgumentException("lembrete no passado: " + quando);
            }
            if (quando.isAfter(agora.plusMonths(12))) {
                throw new IllegalArgumentException("lembrete a mais de 12 meses: " + quando);
            }
        }
    }

    void reservar(OrgId org, UUID pendenciaId, OffsetDateTime agendadoPara, UUID gestorId);

    void repousar(OrgId org, UUID pendenciaId, OffsetDateTime voltaEm, UUID gestorId);

    void descartar(OrgId org, UUID pendenciaId, UUID gestorId);

    String adiar(OrgId org, UUID pendenciaId, OffsetDateTime voltaEm, String oQueFalta, UUID gestorId);
}
