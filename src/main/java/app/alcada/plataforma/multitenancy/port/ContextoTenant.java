package app.alcada.plataforma.multitenancy.port;

import java.util.Optional;

/**
 * Contexto do tenant resolvido a partir do token na entrada da requisição
 * e propagado pela cadeia. INV-15.
 */
public interface ContextoTenant {

    Optional<OrgId> atual();

    default OrgId obrigatorio() {
        return atual().orElseThrow(
                () -> new IllegalStateException("INV-15: nenhum org_id no contexto da requisição"));
    }
}
