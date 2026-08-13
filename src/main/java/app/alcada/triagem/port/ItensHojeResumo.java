package app.alcada.triagem.port;

import java.util.List;
import java.util.UUID;
import app.alcada.plataforma.multitenancy.port.OrgId;

/** Recorte canônico de Hoje publicado para o envelope diário. */
public interface ItensHojeResumo {
    List<Item> listar(OrgId org);
    record Item(UUID pendenciaId, String titulo, String justificativa) {}
}
