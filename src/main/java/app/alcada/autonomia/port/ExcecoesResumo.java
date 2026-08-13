package app.alcada.autonomia.port;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import app.alcada.plataforma.multitenancy.port.OrgId;

/** Exceções acionáveis do motor; leitura escopada sem expor suas tabelas. */
public interface ExcecoesResumo {
    Conteudo listar(OrgId org, UUID gestorId, OffsetDateTime executarAte);
    record Item(UUID pendenciaId, UUID referenciaId, String titulo, OffsetDateTime quando, String trecho) {}
    record Conteudo(List<Item> n2, long totalN2, List<Item> retornos, long totalRetornos,
                    List<Item> escalonamentos, long totalEscalonamentos) {}
}
