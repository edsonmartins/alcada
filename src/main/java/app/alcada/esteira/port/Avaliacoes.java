package app.alcada.esteira.port;

import java.util.List;
import java.util.UUID;

import app.alcada.esteira.port.EntradasEsteira.ApontamentoItem;
import app.alcada.esteira.port.EntradasEsteira.ResultadoItem;
import app.alcada.plataforma.multitenancy.port.OrgId;

/** Avaliação de uma instância: aplica a regra de avanço (RFC-0006). */
public interface Avaliacoes {

    AvaliacaoResultado avaliar(OrgId org, UUID instanciaId,
                               List<ResultadoItem> resultados, List<ApontamentoItem> apontamentos,
                               UUID avaliadorId);
}
