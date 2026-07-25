package app.alcada.regras.port;

import java.time.OffsetDateTime;

/** Regra de autonomia ativa (consumida pelo motor de captura: classe → nivel). */
public record RegraAtiva(String id, String classe, String nivel, String donoId, OffsetDateTime criadaEm) {
}
