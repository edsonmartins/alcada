package app.alcada.esteira.port;

import java.util.List;
import java.util.UUID;

import app.alcada.esteira.port.EntradasEsteira.NovaEtapa;
import app.alcada.esteira.port.EntradasEsteira.NovoCriterio;
import app.alcada.plataforma.multitenancy.port.OrgId;

/** Agregado esteira: montagem, instâncias, avanço e checklist versionado. */
public interface Esteiras {

    UUID criar(OrgId org, String nome, List<NovaEtapa> etapas);

    List<EsteiraDados> listar(OrgId org);

    UUID criarInstancia(OrgId org, UUID esteiraId, String entidadeExterna);

    List<InstanciaDados> instancias(OrgId org, UUID esteiraId, UUID etapaFiltro);

    void avancar(OrgId org, UUID instanciaId);

    ChecklistDados checklistVigente(OrgId org, UUID esteiraId);

    /** Publica NOVA versão do checklist da etapa do gestor (nunca update — ADR-0012). */
    int publicarChecklist(OrgId org, UUID esteiraId, List<NovoCriterio> criterios);
}
