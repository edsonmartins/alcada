package app.alcada.plataforma.multitenancy.internal;

import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Resolve a pessoa (quem age) na entrada da requisição e publica no
 * {@link ContextoPessoaRequest}. Espelha {@link ResolucaoOrgId}:
 *
 * <p>Em produção a pessoa vem da claim {@code pessoa_id} do token OIDC (ArchGuard);
 * o header {@code X-Pessoa-Id} (docs/API.md), se presente, é validado contra ela —
 * ninguém age como outra pessoa forjando o header. Em dev/test/demo (OIDC off) o
 * header é a única fonte. Só propaga o contexto — nenhum efeito depende disto.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 10)
public class ResolucaoPessoa implements ContainerRequestFilter {

    static final String HEADER = "X-Pessoa-Id";

    @Inject
    ContextoPessoaRequest contexto;

    @Inject
    Instance<SecurityIdentity> identidade;

    @Override
    public void filter(ContainerRequestContext req) {
        UUID doToken = pessoaDoToken();
        String header = req.getHeaderString(HEADER);

        UUID resolvida = doToken;
        if (resolvida == null && header != null && !header.isBlank()) {
            resolvida = UUID.fromString(header.trim());
        }
        if (doToken != null && header != null && !header.isBlank()
                && !doToken.equals(UUID.fromString(header.trim()))) {
            req.abortWith(Response.status(403).build());
            return;
        }
        if (resolvida != null) {
            contexto.definir(resolvida);
        }
    }

    private UUID pessoaDoToken() {
        if (identidade.isUnsatisfied()) {
            return null;
        }
        SecurityIdentity id = identidade.get();
        if (id == null || id.isAnonymous()) {
            return null;
        }
        Object claim = id.getAttribute("pessoa_id");
        if (claim == null && id.getPrincipal() instanceof JsonWebToken jwt) {
            claim = jwt.getClaim("pessoa_id");
        }
        return claim == null ? null : UUID.fromString(claim.toString());
    }
}
