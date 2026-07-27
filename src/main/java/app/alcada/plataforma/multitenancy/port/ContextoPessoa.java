package app.alcada.plataforma.multitenancy.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Pessoa (gestor/executor) resolvida na entrada da requisição — do token OIDC em
 * produção, do header {@code X-Pessoa-Id} no piloto. Espelha {@link ContextoTenant}
 * para o eixo da pessoa: quem age. Autenticação de verdade define isto pelo token;
 * o header, quando há token, é validado contra a claim.
 */
public interface ContextoPessoa {

    Optional<UUID> atual();

    default UUID obrigatorio() {
        return atual().orElseThrow(
                () -> new IllegalStateException("pessoa não resolvida na requisição"));
    }
}
