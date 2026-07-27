package app.alcada.movel.port;

import java.math.BigDecimal;
import java.util.List;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Config por tenant do modo trajeto (023): quais decisões são "de peso" e portanto
 * recusadas em movimento (viram bloco). O app lê e aplica offline (a recusa roda
 * no aparelho). Config operacional por org, não cadastro do gestor (INV-02).
 */
public interface ConfigTrajeto {

    Config carregar(OrgId org);

    /**
     * Ajusta a config da org (admin) e devolve o valor normalizado que foi gravado.
     * Invalida o cache após o commit — vale sem reiniciar (nesta instância).
     */
    Config salvar(OrgId org, List<String> classesRecusaveis, BigDecimal valorLimite);

    record Config(List<String> classesRecusaveis, BigDecimal valorLimite) {
    }
}
