package app.alcada.regras.port;

import java.util.List;

import app.alcada.plataforma.multitenancy.port.OrgId;

/** Mineração determinística de regras de autonomia (leitura; sem modelo, INV-10). */
public interface Mineracao {

    List<PropostaRegra> propostas(OrgId org);
}
