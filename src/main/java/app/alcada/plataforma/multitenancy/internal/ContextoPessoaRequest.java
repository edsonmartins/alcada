package app.alcada.plataforma.multitenancy.internal;

import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.ContextoPessoa;
import jakarta.enterprise.context.RequestScoped;

/** Portador da pessoa no escopo da requisição. Preenchido por {@link ResolucaoPessoa}. */
@RequestScoped
public class ContextoPessoaRequest implements ContextoPessoa {

    private UUID pessoaId;

    @Override
    public Optional<UUID> atual() {
        return Optional.ofNullable(pessoaId);
    }

    void definir(UUID pessoaId) {
        this.pessoaId = pessoaId;
    }
}
