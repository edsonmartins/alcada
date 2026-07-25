package app.alcada.plataforma.multitenancy.internal;

import java.util.Optional;

import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.enterprise.context.RequestScoped;

/**
 * Portador do org_id no escopo da requisição. Preenchido por {@link ResolucaoOrgId}.
 */
@RequestScoped
public class ContextoTenantRequest implements ContextoTenant {

    private OrgId orgId;

    @Override
    public Optional<OrgId> atual() {
        return Optional.ofNullable(orgId);
    }

    void definir(OrgId orgId) {
        this.orgId = orgId;
    }
}
