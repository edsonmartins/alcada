package app.alcada.plataforma.gateway.port;

/**
 * Falhas tratadas do gateway. Nenhuma leva a degradar a garantia: schema
 * estrito não vira {@code json_object}, indisponibilidade não vira roteamento
 * fora da lista (ADR-0020).
 */
public final class FalhasGateway {

    private FalhasGateway() {
    }

    /** Provedor sem suporte a {@code json_schema} estrito — falha, nunca degrada. */
    public static class ProvedorSemSchema extends RuntimeException {
        public ProvedorSemSchema(String msg) {
            super(msg);
        }
    }

    /** Todos os provedores homologados indisponíveis (allow_fallbacks:false). */
    public static class Indisponivel extends RuntimeException {
        public Indisponivel(String msg) {
            super(msg);
        }
    }

    /** Roteamento recusado por guardrail (provedor fora da lista `only`). */
    public static class GuardrailRecusou extends RuntimeException {
        public GuardrailRecusou(String msg) {
            super(msg);
        }
    }
}
