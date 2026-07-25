package app.alcada.plataforma.trilha.port;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Um evento a registrar na trilha. Imutável; a persistência é append-only (INV-11).
 *
 * @param org           tenant (INV-15)
 * @param pendenciaId   pendência a que o evento pertence
 * @param tipo          tipo do vocabulário fechado (ADR-0016)
 * @param ator          quem produziu o evento
 * @param estadoAnterior estado da pendência antes (pode ser nulo em CAPTADA)
 * @param estadoPosterior estado da pendência depois
 * @param origemJson    origem (canal, fonte, mensagem_id) como JSON, ou nulo
 * @param cargaJson     campos extras por tipo de evento como JSON, ou nulo
 */
public record EventoTrilha(
        OrgId org,
        UUID pendenciaId,
        TipoEvento tipo,
        Ator ator,
        String estadoAnterior,
        String estadoPosterior,
        String origemJson,
        String cargaJson) {
}
