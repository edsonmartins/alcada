package app.alcada.captura.port;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Marca de que o aviso de bot visível foi publicado num grupo (024 C6). É a
 * pré-condição da captura (ADR-0011 §2): enquanto não publicado, o conteúdo do
 * grupo não é ingerido. Implementada em captura (dona da tabela grupo_acompanhado);
 * chamada pela entrega do outbox após o Linktor confirmar o envio.
 */
public interface AvisoGrupo {

    /** Registra que o aviso foi publicado agora (idempotente por grupo). */
    void marcarPublicado(OrgId org, String grupoId);
}
