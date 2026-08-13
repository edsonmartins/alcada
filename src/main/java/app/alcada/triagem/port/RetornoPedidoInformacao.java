package app.alcada.triagem.port;

import java.util.UUID;
import app.alcada.plataforma.multitenancy.port.OrgId;

/** Fronteira publicada para aplicar um retorno já autenticado e correlacionado. */
public interface RetornoPedidoInformacao {
    boolean responder(OrgId org, UUID pedidoId);
}
