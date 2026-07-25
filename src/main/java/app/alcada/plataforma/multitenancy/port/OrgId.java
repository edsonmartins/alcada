package app.alcada.plataforma.multitenancy.port;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de organização (tenant). INV-15: toda query carrega org_id.
 */
public record OrgId(UUID valor) {
    public OrgId {
        Objects.requireNonNull(valor, "org_id não pode ser nulo");
    }

    public static OrgId de(String texto) {
        return new OrgId(UUID.fromString(texto));
    }
}
