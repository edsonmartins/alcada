package app.alcada.autonomia.internal;

/** Tipos de job do motor. Idempotência: (tipo, chave=delegacao_id) — RFC-0002. */
final class TiposAutonomia {
    static final String VENCIMENTO = "AUT_VENCIMENTO";
    static final String VIRADA = "AUT_VIRADA";
    static final String ESCALONAMENTO = "AUT_ESCALONAMENTO";

    private TiposAutonomia() {
    }
}
