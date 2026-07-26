package app.alcada.autonomia.internal;

/** Tipos de job do motor. Idempotência: (tipo, chave=delegacao_id) — RFC-0002. */
final class TiposAutonomia {
    static final String VENCIMENTO = "AUT_VENCIMENTO";
    static final String VIRADA = "AUT_VIRADA";
    static final String ESCALONAMENTO = "AUT_ESCALONAMENTO";
    static final String LEMBRETE_50 = "AUT_LEMBRETE_50"; // metade do prazo → executor
    static final String LEMBRETE_90 = "AUT_LEMBRETE_90"; // 90% do prazo → gestor

    private TiposAutonomia() {
    }
}
