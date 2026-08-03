package app.alcada.triagem.internal;

/** Falhas de negócio da triagem. */
final class FalhasTriagem {

    private FalhasTriagem() {
    }

    /** 409 pendencia.estado_invalido — saída não permitida no estado atual. */
    static class EstadoInvalido extends RuntimeException {
        EstadoInvalido(String msg) {
            super(msg);
        }
    }

    /** 422 — `o_que_falta` fora do enum (ADR-0002). */
    static class MotivoInvalido extends RuntimeException {
        MotivoInvalido(String msg) {
            super(msg);
        }
    }
}
