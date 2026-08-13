package app.alcada.autonomia.port;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import app.alcada.plataforma.multitenancy.port.OrgId;

/** Correlação fail-closed de respostas de canal com delegações (ADR-0029). */
public interface CorrelacoesRetorno {
    void criar(OrgId org, UUID delegacaoId, String canal, String destino, OffsetDateTime expiraEm);
    void criarParaPedido(OrgId org, UUID pedidoId, String canal, String destino, OffsetDateTime expiraEm);
    Optional<String> tokenParaEnvio(OrgId org, UUID delegacaoId);
    Optional<String> tokenParaPedido(OrgId org, UUID pedidoId);
    Resultado receber(OrgId org, String token, String canal, String autor, String mensagemId,
                      String trechoMinimizado);
    Recepcao receberDetalhado(OrgId org, String token, String canal, String autor, String mensagemId,
                              String trechoMinimizado);
    record Recepcao(Resultado resultado, UUID pedidoInformacaoId) {}
    enum Resultado { OBSERVADO, REPETIDO, NAO_CORRELACIONADO, AUTOR_DIVERGENTE }
}
