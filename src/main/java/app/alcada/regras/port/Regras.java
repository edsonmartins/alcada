package app.alcada.regras.port;

import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/** Regras de autonomia: leitura das ativas e comandos (aceitar/silenciar/desativar). */
public interface Regras {

    List<RegraAtiva> ativas(OrgId org);

    boolean existeRegraAtiva(OrgId org, String classe);

    /** Teto de autonomia da classe (classe_decisao.nivel_maximo) ou null se não configurado. */
    String nivelMaximo(OrgId org, String classe);

    UUID criar(OrgId org, String classe, String nivel, UUID donoId);

    void silenciar(OrgId org, String classe, UUID por);

    void desativar(OrgId org, UUID regraId);
}
