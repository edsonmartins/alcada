package app.alcada.plataforma.multitenancy.internal;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Resolve o org_id na entrada da requisição e o publica no {@link ContextoTenantRequest}.
 *
 * <p>Em produção o org_id vem da claim {@code org_id} do token OIDC; o header
 * {@code X-Org-Id} (docs/API.md) é validado contra ele. Em dev/test (OIDC
 * desabilitado) o header é a única fonte. Nenhum efeito externo depende disso —
 * é só a propagação exigida pela INV-15.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 10)
public class ResolucaoOrgId implements ContainerRequestFilter {

    static final String HEADER = "X-Org-Id";

    @Inject
    ContextoTenantRequest contexto;

    @Inject
    Instance<SecurityIdentity> identidade;

    @Override
    public void filter(ContainerRequestContext req) {
        UUID doToken = orgIdDoToken();
        String header = req.getHeaderString(HEADER);

        UUID resolvido = doToken;
        if (resolvido == null && header != null && !header.isBlank()) {
            resolvido = UUID.fromString(header.trim());
        }
        // Quando há token, o header, se presente, precisa concordar com ele.
        if (doToken != null && header != null && !header.isBlank()
                && !doToken.equals(UUID.fromString(header.trim()))) {
            req.abortWith(jakarta.ws.rs.core.Response.status(403).build());
            return;
        }
        if (resolvido != null) {
            contexto.definir(new OrgId(resolvido));
        }
    }

    private UUID orgIdDoToken() {
        if (identidade.isUnsatisfied()) {
            return null;
        }
        SecurityIdentity id = identidade.get();
        if (id == null || id.isAnonymous()) {
            return null;
        }
        Object claim = id.getAttribute("org_id");
        return claim == null ? null : UUID.fromString(claim.toString());
    }
}
