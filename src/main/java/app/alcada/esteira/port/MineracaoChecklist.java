package app.alcada.esteira.port;

import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/** Mineração de critérios de checklist (RFC-0003 §B) — leitura pura. */
public interface MineracaoChecklist {

    PropostaChecklist propostas(OrgId org, UUID esteiraId);
}
