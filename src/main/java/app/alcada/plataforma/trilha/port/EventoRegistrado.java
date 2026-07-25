package app.alcada.plataforma.trilha.port;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Um evento de trilha já gravado, para leitura. `origem` e `carga` são JSON
 * cru (referências por id — ADR-0016), devolvidos como texto.
 */
public record EventoRegistrado(
        UUID id,
        UUID pendenciaId,
        String tipo,
        String ator,
        OffsetDateTime ocorridoEm,
        String estadoAnterior,
        String estadoPosterior,
        String origem,
        String carga) {
}
