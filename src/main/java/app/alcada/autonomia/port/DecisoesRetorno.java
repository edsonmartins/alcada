package app.alcada.autonomia.port;

import java.util.UUID;
import app.alcada.plataforma.multitenancy.port.OrgId;

public interface DecisoesRetorno {
    void decidir(OrgId org, UUID gestorId, UUID retornoId, String decisao, String idempotencyKey);

    class NaoEncontrado extends RuntimeException { public NaoEncontrado() { super("retorno não encontrado"); } }
    class Proibido extends RuntimeException { public Proibido() { super("retorno não pertence ao gestor"); } }
    class Conflito extends RuntimeException { public Conflito(String mensagem) { super(mensagem); } }
}
