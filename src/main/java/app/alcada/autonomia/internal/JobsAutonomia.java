package app.alcada.autonomia.internal;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.scheduler.port.ExecutorJob;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Executores de job do motor. Cada um roteia por tipo para o handler do
 * {@link MotorAutonomia}. Handlers são idempotentes e guardados pelo status —
 * reinício não perde nem duplica.
 */
public final class JobsAutonomia {

    private static final Pattern DELEGACAO =
            Pattern.compile("\"delegacao_id\"\\s*:\\s*\"([0-9a-fA-F-]+)\"");

    private JobsAutonomia() {
    }

    private static UUID delegacao(String payload) {
        Matcher m = DELEGACAO.matcher(payload == null ? "" : payload);
        return m.find() ? UUID.fromString(m.group(1)) : null;
    }

    @ApplicationScoped
    public static class Vencimento implements ExecutorJob {
        private final MotorAutonomia motor;

        public Vencimento(MotorAutonomia motor) {
            this.motor = motor;
        }

        @Override
        public String tipo() {
            return TiposAutonomia.VENCIMENTO;
        }

        @Override
        public void executar(OrgId org, String chave, String payloadJson) {
            UUID id = delegacao(payloadJson);
            if (id != null) {
                motor.aoVencimento(org, id);
            }
        }
    }

    @ApplicationScoped
    public static class Virada implements ExecutorJob {
        private final MotorAutonomia motor;

        public Virada(MotorAutonomia motor) {
            this.motor = motor;
        }

        @Override
        public String tipo() {
            return TiposAutonomia.VIRADA;
        }

        @Override
        public void executar(OrgId org, String chave, String payloadJson) {
            UUID id = delegacao(payloadJson);
            if (id != null) {
                motor.aVirada(org, id);
            }
        }
    }

    @ApplicationScoped
    public static class Lembrete50 implements ExecutorJob {
        private final MotorAutonomia motor;

        public Lembrete50(MotorAutonomia motor) {
            this.motor = motor;
        }

        @Override
        public String tipo() {
            return TiposAutonomia.LEMBRETE_50;
        }

        @Override
        public void executar(OrgId org, String chave, String payloadJson) {
            UUID id = delegacao(payloadJson);
            if (id != null) {
                motor.aoLembrete(org, id, false);
            }
        }
    }

    @ApplicationScoped
    public static class Lembrete90 implements ExecutorJob {
        private final MotorAutonomia motor;

        public Lembrete90(MotorAutonomia motor) {
            this.motor = motor;
        }

        @Override
        public String tipo() {
            return TiposAutonomia.LEMBRETE_90;
        }

        @Override
        public void executar(OrgId org, String chave, String payloadJson) {
            UUID id = delegacao(payloadJson);
            if (id != null) {
                motor.aoLembrete(org, id, true);
            }
        }
    }

    @ApplicationScoped
    public static class Escalonamento implements ExecutorJob {
        private final MotorAutonomia motor;

        public Escalonamento(MotorAutonomia motor) {
            this.motor = motor;
        }

        @Override
        public String tipo() {
            return TiposAutonomia.ESCALONAMENTO;
        }

        @Override
        public void executar(OrgId org, String chave, String payloadJson) {
            UUID id = delegacao(payloadJson);
            if (id != null) {
                motor.aoEscalonamento(org, id);
            }
        }
    }
}
