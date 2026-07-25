package app.alcada.esteira.port;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Portal externo da instância (RFC-0006 / ADR-0013): link assinado sem login. O
 * token é a credencial (banco guarda só o hash). Projeção curada — nunca expõe
 * deliberação interna, decisores nem outras contrapartes.
 */
public interface PortalInstancia {

    TokenEmitido emitir(OrgId org, UUID instanciaId, OffsetDateTime expiraEm);

    boolean revogar(OrgId org, UUID tokenId);

    /** Resolve o token cru para a projeção pública; empty uniforme para inválido/expirado/revogado. */
    Optional<EstadoInstancia> resolver(String tokenCru);

    /** Declaração de conformidade da contraparte (informa o gestor; não decide — INV-10). */
    void autoavaliar(String tokenCru, List<Declaracao> declaracoes);

    record TokenEmitido(String tokenId, String token) {
    }

    record Declaracao(String criterioChave, boolean conforme) {
    }

    record EstadoInstancia(String esteiraNome, String etapaAtualNome, OffsetDateTime entrouEm,
                           OffsetDateTime prazoPrevisto, List<ItemFalta> oQueFalta) {

        public record ItemFalta(String chave, String descricao) {
        }
    }
}
